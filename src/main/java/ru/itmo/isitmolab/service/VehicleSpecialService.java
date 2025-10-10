package ru.itmo.isitmolab.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import ru.itmo.isitmolab.dao.VehicleSpecialDao;
import ru.itmo.isitmolab.dto.VehicleDto;
import ru.itmo.isitmolab.model.Vehicle;

import java.util.*;

/**
 * Специализированные запросы/агрегации по {@link Vehicle}, поверх обычного DAO.
 *
 * <h3>Особенности</h3>
 * <ul>
 *   <li>Загрузка сущностей с именованным {@link EntityGraph} <b>Vehicle.withOwnerAdmin</b> для предотвращения N+1.</li>
 *   <li>Двухшаговые выборки: сначала список {@code id} из {@link VehicleSpecialDao}, затем загрузка сущностей
 *       по этим {@code id} с сохранением исходного порядка.</li>
 *   <li>Транзакционность на чтение у публичных методов, чтобы хинты графа стабильно применялись в одном контексте.</li>
 * </ul>
 *
 * <h3>Замечания для продакшена</h3>
 * <ul>
 *   <li>Убедитесь, что на {@code Vehicle} объявлен {@code @NamedEntityGraph(name = "Vehicle.withOwnerAdmin", ...)}.</li>
 *   <li>Методы, возвращающие коллекции DTO, всегда маппят через {@link VehicleDto#toDto(Vehicle)}.</li>
 *   <li>Если добавите кэш 2-го уровня — учитывайте, что загрузка с loadgraph может влиять на кешируемые ассоциации.</li>
 * </ul>
 */
@ApplicationScoped
public class VehicleSpecialService {

    @Inject
    VehicleSpecialDao specialDao;

    @PersistenceContext(unitName = "studsPU")
    EntityManager em;

    /**
     * Получить именованный граф для подгрузки ассоциаций {@code owner}, {@code admin}.
     *
     * @return {@link EntityGraph} для {@link Vehicle}
     * @throws IllegalArgumentException если граф не объявлен на сущности
     */
    @SuppressWarnings("unchecked")
    private EntityGraph<Vehicle> graph() {
        return (EntityGraph<Vehicle>) em.createEntityGraph("Vehicle.withOwnerAdmin");
    }

    /* =============== helpers =============== */

    /**
     * Загрузить один {@link Vehicle} по ID с применением loadgraph.
     *
     * @param id идентификатор (может быть {@code null})
     * @return Optional с найденной сущностью или пустой Optional
     */
    private Optional<Vehicle> loadOne(Long id) {
        if (id == null) return Optional.empty();
        Map<String, Object> hints = Map.of("jakarta.persistence.loadgraph", graph());
        // find(..., hints) применяет EntityGraph только к этому чтению
        return Optional.ofNullable(em.find(Vehicle.class, id, hints));
    }

    /**
     * Загрузить список {@link Vehicle} по наборам ID с применением loadgraph и
     * сохранить порядок элементов в соответствии с порядком входного списка ID.
     *
     * @param ids упорядоченный список идентификаторов; допускается пустой/null
     * @return список сущностей в исходном порядке ID; пропущенные ID игнорируются
     */
    private List<Vehicle> loadManyPreserveOrder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();

        List<Vehicle> items = em.createQuery(
                        "select v from Vehicle v where v.id in :ids", Vehicle.class)
                .setParameter("ids", ids)
                .setHint("jakarta.persistence.loadgraph", graph())
                .getResultList();

        // Сохраняем порядок как в ids: индексируем по id и собираем в том же порядке.
        Map<Long, Vehicle> byId = new HashMap<>(items.size() * 2);
        for (Vehicle v : items) byId.put(v.getId(), v);

        List<Vehicle> ordered = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Vehicle v = byId.get(id);
            if (v != null) ordered.add(v); // если id отсутствует в БД — пропускаем
        }
        return ordered;
    }

    /* =============== API =============== */

    /**
     * Найти любой {@link Vehicle} с минимальным пробегом/дистанцией (критерий определён в DAO),
     * загрузить его с графом и вернуть как DTO.
     *
     * <p><b>Транзакционность:</b> read-only; оборачиваем в транзакцию для стабильной работы хинтов/ленивых связей.</p>
     *
     * @return Optional DTO найденного транспорта или пустой Optional
     */
    @Transactional
    public Optional<VehicleDto> findAnyWithMinDistance() {
        return specialDao.findAnyWithMinDistanceId()
                .flatMap(this::loadOne)
                .map(VehicleDto::toDto);
    }

    /**
     * Подсчитать количество транспортов с расходом топлива строго больше заданного.
     *
     * @param v порог расхода (fuelConsumption)
     * @return количество записей
     */
    public long countFuelConsumptionGreaterThan(float v) {
        return specialDao.countFuelConsumptionGreaterThan(v);
    }

    /**
     * Список транспортов с расходом топлива > v, в исходном порядке ID из DAO.
     *
     * @param v порог расхода
     * @return список DTO (может быть пустым)
     */
    @Transactional
    public List<VehicleDto> listFuelConsumptionGreaterThan(float v) {
        List<Long> ids = specialDao.listFuelConsumptionGreaterThanIds(v);
        return loadManyPreserveOrder(ids).stream().map(VehicleDto::toDto).toList();
    }

    /**
     * Список транспортов по типу (строковое значение типа), в исходном порядке ID из DAO.
     *
     * @param type строковый тип (например, "TRUCK"); чувствительность к регистру определяется реализацией DAO
     * @return список DTO (может быть пустым)
     */
    @Transactional
    public List<VehicleDto> listByType(String type) {
        List<Long> ids = specialDao.listByTypeIds(type);
        return loadManyPreserveOrder(ids).stream().map(VehicleDto::toDto).toList();
    }

    /**
     * Список транспортов с мощностью двигателя в диапазоне [min; max] (границы включительно/исключительно — как в DAO),
     * с сохранением порядка ID.
     *
     * @param min нижняя граница (может быть {@code null} — тогда без нижнего ограничения)
     * @param max верхняя граница (может быть {@code null} — тогда без верхнего ограничения)
     * @return список DTO (может быть пустым)
     */
    @Transactional
    public List<VehicleDto> listByEnginePowerBetween(Integer min, Integer max) {
        List<Long> ids = specialDao.listByEnginePowerBetweenIds(min, max);
        return loadManyPreserveOrder(ids).stream().map(VehicleDto::toDto).toList();
    }
}
