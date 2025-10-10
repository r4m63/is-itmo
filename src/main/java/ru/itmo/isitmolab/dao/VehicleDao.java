package ru.itmo.isitmolab.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import jakarta.transaction.Transactional;
import ru.itmo.isitmolab.dto.GridTableRequest;
import ru.itmo.isitmolab.model.Person;
import ru.itmo.isitmolab.model.Vehicle;
import ru.itmo.isitmolab.util.gridtable.GridTablePredicateBuilder;

import java.util.*;

/**
 * DAO-слой для работы с сущностями {@link Vehicle}.
 *
 * <h3>Особенности реализации</h3>
 * <ul>
 *   <li>Использует JPA Criteria API для построения динамических запросов под грид (фильтры/сортировка/пагинация).</li>
 *   <li>Для избежания N+1 при постраничной выборке применяется подход "двух проходов":
 *       сначала выбираются <b>ID</b> требуемой страницы, затем сущности грузятся по этим ID
 *       с {@link EntityGraph} (см. {@link #findPageByGrid(GridTableRequest)}).</li>
 *   <li>Чтения выполняются без явной транзакции (контейнер управляет), изменения — под @Transactional.</li>
 *   <li>Массовые апдейты выполняются JPQL-операторами <code>update</code> для производительности
 *       (обходят загрузку сущностей и callbacks; см. caveats к {@link #reassignOwner(Long, Long)}).</li>
 * </ul>
 *
 * <h3>Продакшен-заметки</h3>
 * <ul>
 *   <li>Индексы в БД: рекомендуется индекс по <code>vehicle(id)</code>, <code>vehicle(creation_time)</code>,
 *       <code>vehicle(owner_id)</code> для запросов сортировки/фильтрации.</li>
 *   <li>Пагинация: текущая реализация — <i>offset/limit</i>; для больших таблиц подумайте о keyset-пагинации.</li>
 *   <li>EntityGraph: ожидает именованный граф <code>Vehicle.withOwnerAdmin</code>; если его нет — строит динамический граф.</li>
 *   <li>Кэш: bulk-операции JPQL минуют 1-й уровень кэша и 2-й уровень — при необходимости вручную
 *       инвалидируйте кэш/контекст в сервисном слое.</li>
 * </ul>
 */
@ApplicationScoped
public class VehicleDao {

    @PersistenceContext(unitName = "studsPU")
    EntityManager em;

    /**
     * Сохранить сущность: <b>persist</b> при {@code id == null}, иначе <b>merge</b>.
     *
     * <p><b>Транзакционность:</b> требуется активная транзакция (помечено @Transactional).</p>
     *
     * @param v сущность для сохранения (не {@code null})
     */
    @Transactional
    public void save(Vehicle v) {
        if (v.getId() == null) {
            em.persist(v);
        } else {
            em.merge(v);
        }
    }

    /**
     * Найти {@link Vehicle} по идентификатору.
     *
     * @param id идентификатор (может быть {@code null})
     * @return Optional c сущностью или пустой, если {@code id == null} или не найдено
     */
    public Optional<Vehicle> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(em.find(Vehicle.class, id));
    }

    /**
     * Проверить существование сущности по идентификатору.
     *
     * @param id идентификатор (может быть {@code null})
     * @return {@code true} если запись существует; иначе {@code false}
     */
    public boolean existsById(Long id) {
        if (id == null) return false;
        Long cnt = em.createQuery("select count(v) from Vehicle v where v.id = :id", Long.class)
                .setParameter("id", id)
                .getSingleResult();
        return cnt != null && cnt > 0;
    }

    /**
     * Удалить сущность по идентификатору (если существует).
     *
     * <p><b>Транзакционность:</b> требуется активная транзакция.</p>
     *
     * @param id идентификатор (если {@code null} — метод ничего не делает)
     */
    @Transactional
    public void deleteById(Long id) {
        if (id == null) return;
        Vehicle ref = em.find(Vehicle.class, id);
        if (ref != null) em.remove(ref);
    }

    /**
     * Получить все записи, отсортированные по убыванию creationTime, затем id.
     *
     * <p>Для больших наборов данных используйте {@link #findAll(int, int)}.</p>
     *
     * @return список сущностей (может быть пустым)
     */
    public List<Vehicle> findAll() {
        return em.createQuery(
                "select v from Vehicle v order by v.creationTime desc, v.id desc", Vehicle.class
        ).getResultList();
    }

    /**
     * Постраничная выборка всех записей с сортировкой по creationTime desc, id desc.
     *
     * @param offset смещение (отрицательные значения нормализуются к 0)
     * @param limit  размер страницы (минимум 1)
     * @return список сущностей для указанного окна
     */
    public List<Vehicle> findAll(int offset, int limit) {
        return em.createQuery(
                        "select v from Vehicle v order by v.creationTime desc, v.id desc", Vehicle.class
                )
                .setFirstResult(Math.max(0, offset))
                .setMaxResults(Math.max(1, limit))
                .getResultList();
    }

    /**
     * Постраничная выборка под грид: фильтры/сортировка из {@link GridTableRequest}, затем загрузка сущностей
     * с графом (owner, admin) и восстановление порядка как в вычисленном списке ID.
     *
     * <h4>Алгоритм (2-шаговый)</h4>
     * <ol>
     *   <li>CriteriaQuery&lt;Long&gt;: применить фильтры/сортировку, выбрать только {@code id}, задать offset/limit.</li>
     *   <li>Загрузить сущности по этим {@code id} одним запросом с {@link EntityGraph} (owner, admin),
     *       затем отсортировать в памяти согласно порядку {@code ids} (стабильная сортировка).</li>
     * </ol>
     *
     * <p>Подход минимизирует перетаскивание больших объектов и предотвращает N+1 при наличии связей.</p>
     *
     * @param req параметры грид-таблицы: {@code startRow}, {@code endRow}, {@code sortModel}, {@code filterModel}
     * @return список сущностей для указанной страницы; может быть пустым
     */
    public List<Vehicle> findPageByGrid(GridTableRequest req) {
        final int pageSize = Math.max(1, req.endRow - req.startRow);
        final int first = Math.max(0, req.startRow);

        CriteriaBuilder cb = em.getCriteriaBuilder();

        // ---------- Шаг 1: получаем страницу ID ----------
        CriteriaQuery<Long> idCq = cb.createQuery(Long.class);
        Root<Vehicle> idRoot = idCq.from(Vehicle.class);

        List<Predicate> predicates = GridTablePredicateBuilder.build(cb, idRoot, req.filterModel);
        if (!predicates.isEmpty()) idCq.where(predicates.toArray(new Predicate[0]));

        if (req.sortModel != null && !req.sortModel.isEmpty()) {
            List<Order> orders = new ArrayList<>();
            req.sortModel.forEach(s -> {
                Path<?> p = GridTablePredicateBuilder.resolvePath(idRoot, s.getColId());
                orders.add("desc".equalsIgnoreCase(s.getSort()) ? cb.desc(p) : cb.asc(p));
            });
            idCq.orderBy(orders);
        } else {
            idCq.orderBy(cb.desc(idRoot.get("creationTime")), cb.desc(idRoot.get("id")));
        }

        idCq.select(idRoot.get("id"));

        List<Long> ids = em.createQuery(idCq)
                .setFirstResult(first)
                .setMaxResults(pageSize)
                .getResultList();

        if (ids.isEmpty()) return List.of();

        // ---------- Шаг 2: грузим сущности по этим id с графом ----------
        EntityGraph<Vehicle> graph = getWithOwnerAdminGraph();

        List<Vehicle> items = em.createQuery(
                        "select v from Vehicle v where v.id in :ids", Vehicle.class)
                .setParameter("ids", ids)
                .setHint("jakarta.persistence.loadgraph", graph)
                .getResultList();

        // ---------- Сохраняем порядок как в ids ----------
        Map<Long, Integer> rank = new HashMap<>(ids.size() * 2);
        for (int i = 0; i < ids.size(); i++) rank.put(ids.get(i), i);
        items.sort(Comparator.comparingInt(v -> rank.getOrDefault(v.getId(), Integer.MAX_VALUE)));

        return items;
    }

    /**
     * Подсчитать общее количество записей, удовлетворяющих фильтрам грид-таблицы.
     *
     * @param req параметры фильтрации
     * @return количество записей
     */
    public long countByGrid(GridTableRequest req) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cnt = cb.createQuery(Long.class);
        Root<Vehicle> root = cnt.from(Vehicle.class);

        List<Predicate> preds = GridTablePredicateBuilder.build(cb, root, req.filterModel);
        cnt.select(cb.count(root));
        if (!preds.isEmpty()) cnt.where(preds.toArray(new Predicate[0]));

        return em.createQuery(cnt).getSingleResult();
    }

    /**
     * Получить {@link EntityGraph} для загрузки связей owner и admin.
     *
     * <p>Порядок разрешения:
     * <ol>
     *   <li>Попробовать именованный граф <code>Vehicle.withOwnerAdmin</code> (если объявлен на сущности);</li>
     *   <li>Иначе создать динамический граф с атрибутами <code>owner</code>, <code>admin</code>.</li>
     * </ol>
     * </p>
     *
     * @return граф загрузки для {@link Vehicle}
     */
    @SuppressWarnings("unchecked")
    private EntityGraph<Vehicle> getWithOwnerAdminGraph() {
        try {
            // если добавлен @NamedEntityGraph(name="Vehicle.withOwnerAdmin")
            return (EntityGraph<Vehicle>) em.getEntityGraph("Vehicle.withOwnerAdmin");
        } catch (IllegalArgumentException ex) {
            // fallback: динамический граф
            EntityGraph<Vehicle> g = em.createEntityGraph(Vehicle.class);
            g.addAttributeNodes("owner", "admin");
            return g;
        }
    }

    /**
     * Подсчитать количество транспортов по идентификатору владельца.
     *
     * @param ownerId ID владельца
     * @return количество записей
     */
    public long countByOwnerId(Long ownerId) {
        return em.createQuery(
                        "select count(v) from Vehicle v where v.owner.id = :oid", Long.class)
                .setParameter("oid", ownerId)
                .getSingleResult();
    }

    /**
     * Массово переназначить владельца с {@code fromPersonId} на {@code toPersonId}.
     *
     * <p><b>Производственный caveat:</b> JPQL bulk-update:
     * <ul>
     *   <li>обходит загрузку сущностей и не вызывает entity callbacks (@PreUpdate и т.п.);</li>
     *   <li>не уважает поле версионирования и может рассинхронизировать persistence context/2-й уровень кэша;</li>
     *   <li>возвращает количество затронутых строк.</li>
     * </ul>
     * При необходимости — после вызова очищайте контекст/кэш на уровне сервиса.</p>
     *
     * <p><b>Транзакционность:</b> требуется активная транзакция.</p>
     *
     * @param fromPersonId текущий владелец (обязателен; если хотите поддержать {@code null}, адаптируйте JPQL)
     * @param toPersonId   новый владелец (обязателен, должен существовать)
     * @return количество обновленных записей
     */
    @Transactional
    public int reassignOwner(Long fromPersonId, Long toPersonId) {
        Person toRef = em.getReference(Person.class, toPersonId);
        // Bulk update — быстро и без загрузки сущностей в память
        return em.createQuery(
                        "update Vehicle v set v.owner = :to where v.owner.id = :from")
                .setParameter("to", toRef)
                .setParameter("from", fromPersonId)
                .executeUpdate();
    }

    /**
     * Найти все транспорты по владельцу.
     *
     * @param ownerId ID владельца
     * @return список сущностей, отсортированный по {@code id asc}
     */
    public List<Vehicle> findByOwner(Long ownerId) {
        return em.createQuery("select v from Vehicle v where v.owner.id = :oid order by v.id asc", Vehicle.class)
                .setParameter("oid", ownerId)
                .getResultList();
    }
}
