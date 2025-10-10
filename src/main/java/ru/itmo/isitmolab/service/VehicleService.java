package ru.itmo.isitmolab.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ru.itmo.isitmolab.dao.AdminDao;
import ru.itmo.isitmolab.dao.PersonDao;
import ru.itmo.isitmolab.dao.VehicleDao;
import ru.itmo.isitmolab.dto.GridTableRequest;
import ru.itmo.isitmolab.dto.GridTableResponse;
import ru.itmo.isitmolab.dto.VehicleDto;
import ru.itmo.isitmolab.model.Admin;
import ru.itmo.isitmolab.model.Vehicle;
import ru.itmo.isitmolab.ws.VehicleWsHub;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Сервисный слой для управления сущностями {@link Vehicle}.
 * <p>
 * Отвечает за:
 * <ul>
 *     <li>Создание, чтение, обновление и удаление транспортных средств;</li>
 *     <li>Конвертацию между {@link Vehicle} и {@link VehicleDto};</li>
 *     <li>Бизнес-проверки (наличие владельца, валидность администратора, запрет некорректных операций);</li>
 *     <li>Уведомление клиентов через {@link VehicleWsHub} после изменений (broadcast "refresh").</li>
 * </ul>
 *
 * <h3>Контракты и соглашения</h3>
 * <ul>
 *   <li>Ошибки бизнес-логики и отсутствие ресурсов сигнализируются через {@link WebApplicationException}
 *       с соответствующим {@link Response.Status} (например, 400, 401, 404).</li>
 *   <li>Единый формат ошибок рекомендуется оформлять глобальным {@code ExceptionMapper}ом
 *       (например, RFC7807 application/problem+json). В данном классе местами возвращается
 *       JSON-объект с ключом {@code message} напрямую при ошибке создания.</li>
 *   <li>Методы, изменяющие состояние, оповещают клиентов через WebSocket-хаб вызовом {@code wsHub.broadcastText("refresh")}.</li>
 *   <li>Проверка текущего администратора производится через {@link SessionService#getCurrentUserId(HttpServletRequest)}.</li>
 * </ul>
 */
@ApplicationScoped
public class VehicleService {

    @Inject
    private VehicleDao dao;

    @Inject
    private AdminDao adminDao;

    @Inject
    private SessionService sessionService;

    @Inject
    private VehicleWsHub wsHub;

    @Inject
    private VehicleDao vehicleDao;

    @Inject
    private PersonDao personDao;

    /**
     * Создает новый {@link Vehicle} на основе переданного {@link VehicleDto}.
     * <p>
     * Бизнес-правила:
     * <ul>
     *   <li>Должен существовать текущий администратор (получается из сессии);</li>
     *   <li>{@code ownerId} обязателен и должен ссылаться на существующую {@code Person};</li>
     *   <li>После успешного сохранения рассылается событие "refresh" в {@link VehicleWsHub}.</li>
     * </ul>
     *
     * @param dto валидируемый DTO с данными транспорта; {@code dto.ownerId} обязателен
     * @param req HTTP-запрос для извлечения контекста текущей сессии/пользователя
     * @return идентификатор созданного {@link Vehicle}
     *
     * @throws WebApplicationException
     *         <ul>
     *           <li>401 UNAUTHORIZED — если не найден администратор по id из сессии;</li>
     *           <li>400 BAD_REQUEST — если {@code ownerId} отсутствует;</li>
     *           <li>400 BAD_REQUEST — если владелец с указанным id не найден
     *               (ответ в формате JSON с ключом {@code message}).</li>
     *         </ul>
     */
    public Long createNewVehicle(VehicleDto dto, HttpServletRequest req) {
        Long adminId = sessionService.getCurrentUserId(req);
        Admin admin = adminDao.findById(adminId)
                .orElseThrow(() -> new WebApplicationException(
                        "Admin not found: " + adminId, Response.Status.UNAUTHORIZED));

        if (dto.getOwnerId() == null) {
            throw new WebApplicationException("ownerId is required", Response.Status.BAD_REQUEST);
        }

        var owner = personDao.findById(dto.getOwnerId())
                .orElseThrow(() -> new WebApplicationException(
                        Response.status(Response.Status.BAD_REQUEST)
                                .type(MediaType.APPLICATION_JSON_TYPE)
                                .entity(Map.of("message", "Не найден owner с id " + dto.getOwnerId()))
                                .build()
                ));

        Vehicle v = VehicleDto.toEntity(dto, null);
        v.setAdmin(admin);
        v.setOwner(owner);

        dao.save(v);
        wsHub.broadcastText("refresh");
        return v.getId();
    }

    /**
     * Полностью обновляет существующий {@link Vehicle}.
     * <p>
     * Обновляет поля из {@link VehicleDto}. Если {@code ownerId} в DTO задан и отличается от текущего,
     * выполняется загрузка нового владельца и переустановка связи.
     * После успешного сохранения отправляется событие "refresh" в WebSocket-хаб.
     *
     * @param id  идентификатор существующего транспорта
     * @param dto новые данные (часть полей может быть null — будет зависеть от логики {@link VehicleDto#toEntity})
     *
     * @throws WebApplicationException
     *         <ul>
     *           <li>404 NOT_FOUND — если транспорт с таким id отсутствует;</li>
     *           <li>400 BAD_REQUEST — если указанный в DTO владелец не найден.</li>
     *         </ul>
     */
    public void updateVehicle(Long id, VehicleDto dto) {
        Vehicle current = dao.findById(id)
                .orElseThrow(() -> new WebApplicationException(
                        "Vehicle not found: " + id, Response.Status.NOT_FOUND));

        if (dto.getOwnerId() != null &&
                (current.getOwner() == null || !dto.getOwnerId().equals(current.getOwner().getId()))) {
            var newOwner = personDao.findById(dto.getOwnerId())
                    .orElseThrow(() -> new WebApplicationException(
                            "Person (owner) not found: " + dto.getOwnerId(), Response.Status.BAD_REQUEST));
            current.setOwner(newOwner);
        }

        VehicleDto.toEntity(dto, current);
        dao.save(current);
        wsHub.broadcastText("refresh");
    }

    /**
     * Возвращает {@link VehicleDto} по идентификатору.
     *
     * @param id идентификатор транспорта
     * @return DTO найденного транспорта
     *
     * @throws WebApplicationException 404 NOT_FOUND — если транспорт не найден
     */
    public VehicleDto getVehicleById(Long id) {
        Vehicle v = dao.findById(id)
                .orElseThrow(() -> new WebApplicationException(
                        "Vehicle not found: " + id, Response.Status.NOT_FOUND));
        return VehicleDto.toDto(v);
    }

    /**
     * Удаляет транспорт по идентификатору.
     * <p>После успешного удаления рассылается "refresh" по WebSocket.</p>
     *
     * @param id идентификатор удаляемого транспорта
     *
     * @throws WebApplicationException 404 NOT_FOUND — если транспорт не существует
     */
    public void deleteVehicleById(Long id) {
        if (!dao.existsById(id)) {
            throw new WebApplicationException(
                    "Vehicle not found: " + id, Response.Status.NOT_FOUND);
        }
        dao.deleteById(id);
        wsHub.broadcastText("refresh");
    }

    /**
     * Возвращает все транспорты в виде {@link VehicleDto}.
     * <p><b>Замечание:</b> для продакшена рекомендуется использовать пагинацию, чтобы не перегружать канал/память.</p>
     *
     * @return неизменяемый список DTO (может быть пустым, но не {@code null})
     */
    public List<VehicleDto> getAllVehicles() {
        return dao.findAll().stream().map(VehicleDto::toDto).toList();
    }

    /**
     * Выполняет постраничный/табличный запрос по критериям грид-таблицы.
     * <p>Метод делегирует построение запроса DAO-слою, а затем маппит сущности в DTO.</p>
     *
     * @param req параметры фильтрации/сортировки/пагинации
     * @return ответ грид-таблицы с элементами {@link VehicleDto} и общим количеством записей
     */
    public GridTableResponse<VehicleDto> queryTableGridFilters(GridTableRequest req) {
        List<Vehicle> rows = vehicleDao.findPageByGrid(req);
        long total = vehicleDao.countByGrid(req);
        List<VehicleDto> dtos = rows.stream()
                .map(VehicleDto::toDto)
                .toList();

        return new GridTableResponse<>(dtos, (int) total);
    }

    /**
     * Ищет все {@link Vehicle} по идентификатору владельца.
     *
     * @param ownerId идентификатор владельца
     * @return список сущностей транспортов, принадлежащих владельцу (может быть пустым, но не {@code null})
     */
    public List<Vehicle> findByOwner(Long ownerId) {
        return vehicleDao.findByOwner(ownerId);
    }

    /**
     * Массово переназначает владельца для набора транспортов.
     * <p>
     * Правила и проверки:
     * <ul>
     *   <li>Запрещено переназначать на того же самого владельца ({@code fromOwnerId == toOwnerId});</li>
     *   <li>Целевой владелец {@code toOwnerId} должен существовать;</li>
     *   <li>Операция транзакционная: либо все применится, либо откатится;</li>
     *   <li>Метод возвращает количество обновленных записей.</li>
     * </ul>
     *
     * @param fromOwnerId текущий владелец (может быть {@code null}, если нужно переназначить все без владельца
     *                    или все записи независимо от текущего — в зависимости от реализации DAO)
     * @param toOwnerId   новый владелец (обязателен и должен существовать)
     * @return количество обновлённых записей
     *
     * @throws WebApplicationException
     *         <ul>
     *           <li>400 BAD_REQUEST — если {@code fromOwnerId} и {@code toOwnerId} равны;</li>
     *           <li>400 BAD_REQUEST — если {@code toOwnerId} не существует.</li>
     *         </ul>
     */
    @Transactional
    public int reassignOwnerBulk(Long fromOwnerId, Long toOwnerId) {
        if (Objects.equals(fromOwnerId, toOwnerId)) {
            throw new WebApplicationException("Нельзя переназначать на того же владельца", Response.Status.BAD_REQUEST);
        }
        if (!personDao.existsById(toOwnerId)) {
            throw new WebApplicationException("Целевой владелец не найден: " + toOwnerId, Response.Status.BAD_REQUEST);
        }
        return vehicleDao.reassignOwner(fromOwnerId, toOwnerId);
    }
}
