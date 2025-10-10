package ru.itmo.isitmolab.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import ru.itmo.isitmolab.dao.AdminDao;
import ru.itmo.isitmolab.dao.PersonDao;
import ru.itmo.isitmolab.dao.VehicleDao;
import ru.itmo.isitmolab.dto.GridTableRequest;
import ru.itmo.isitmolab.dto.GridTableResponse;
import ru.itmo.isitmolab.dto.PersonDto;
import ru.itmo.isitmolab.model.Person;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Сервис управления владельцами ({@link Person}) и связанными транспортными средствами.
 *
 * <h3>Ответственность</h3>
 * <ul>
 *   <li>Поиск/листинг/постраничные выборки владельцев с производительным подсчётом количества ТС;</li>
 *   <li>CRUD-операции над {@link Person} с привязкой к текущему администратору;</li>
 *   <li>Безопасное удаление владельцев с учётом внешних связей (ТС) и поддержкой переназначения.</li>
 * </ul>
 *
 * <h3>Замечания для продакшена</h3>
 * <ul>
 *   <li>Единый формат ошибок (например, RFC7807) желательно реализовать через глобальные {@code ExceptionMapper}’ы.</li>
 *   <li>Постраничные методы используют DAO-уровень для подсчётов, избегая ленивой инициализации коллекций (см. {@code countVehiclesForPersonIds}).</li>
 *   <li>Операции, меняющие связности (массовые апдейты), следует выполнять в транзакции.</li>
 * </ul>
 */
@ApplicationScoped
public class PersonService {

    @Inject
    PersonDao personDao;
    @Inject
    AdminDao adminDao;
    @Inject
    SessionService sessionService;
    @Inject
    VehicleDao vehicleDao;

    /**
     * Постраничный поиск владельцев по параметрам грид-таблицы.
     *
     * <p>Для каждого владельца дополнительно возвращает количество связанных ТС без ленивой загрузки коллекций —
     * через агрегирующий запрос {@link PersonDao#countVehiclesForPersonIds(List)}.</p>
     *
     * @param req параметры фильтров/сортировки/страницы
     * @return страница DTO владельцев и общее количество записей
     */
    public GridTableResponse<PersonDto> query(GridTableRequest req) {
        List<Person> rows = personDao.findPageByGrid(req);
        long total = personDao.countByGrid(req);

        // Считаем qty без ленивой инициализации коллекции
        Map<Long, Integer> counts = personDao.countVehiclesForPersonIds(
                rows.stream().map(Person::getId).toList()
        );

        List<PersonDto> dtos = rows.stream()
                .map(p -> PersonDto.toDto(p, counts.getOrDefault(p.getId(), 0)))
                .toList();
        return new GridTableResponse<>(dtos, (int) total);
    }

    /**
     * Получить владельца по ID с количеством его ТС.
     *
     * @param id идентификатор владельца
     * @return DTO владельца + число связанных ТС
     * @throws WebApplicationException 404 — если владелец не найден
     */
    public PersonDto getOne(Long id) {
        Person p = personDao.findById(id)
                .orElseThrow(() -> new WebApplicationException("Person not found: " + id, Response.Status.NOT_FOUND));

        int cnt = personDao.countVehiclesForPersonId(id);
        return PersonDto.toDto(p, cnt);
    }

    /**
     * Создать нового владельца и привязать его к текущему администратору из сессии.
     *
     * @param dto данные владельца
     * @param req HTTP-запрос (контекст сессии)
     * @return ID созданного владельца
     * @throws WebApplicationException 401 — если администратор из сессии не найден/невалиден
     */
    public Long create(PersonDto dto, HttpServletRequest req) {
        Long adminId = sessionService.getCurrentUserId(req);
        var admin = adminDao.findById(adminId)
                .orElseThrow(() -> new WebApplicationException("Unauthorized", Response.Status.UNAUTHORIZED));

        Person p = new Person();
        PersonDto.apply(dto, p);
        p.setAdmin(admin);                 // admin берём из сессии
        personDao.save(p);
        return p.getId();
    }

    /**
     * Обновить данные владельца.
     *
     * <p>В текущей реализации изменяется только {@code fullName} через {@link PersonDto#apply(PersonDto, Person)}.</p>
     *
     * @param id  идентификатор владельца
     * @param dto изменяемые поля
     * @param req HTTP-запрос (резерв на будущие проверки прав)
     * @throws WebApplicationException 404 — если владелец не найден
     */
    public void update(Long id, PersonDto dto, HttpServletRequest req) {
        Person p = personDao.findById(id)
                .orElseThrow(() -> new WebApplicationException("Person not found: " + id, Response.Status.NOT_FOUND));

        // правим только fullName (согласно логике DTO.apply)
        PersonDto.apply(dto, p);
        personDao.save(p);
    }

    /**
     * Жёсткое удаление владельца без дополнительных проверок.
     *
     * <p><b>Внимание:</b> используйте {@link #deleteGuarded(Long)} или {@link #deletePerson(Long, Long)}
     * для корректной обработки внешних связей.</p>
     *
     * @param id идентификатор владельца
     */
    public void delete(Long id) {
        personDao.deleteById(id);
    }

    /**
     * Короткий список всех владельцев (упорядоченный), сведённый к компактному DTO.
     *
     * @return список {@link PersonDto} (short-форма)
     */
    public List<PersonDto> listAllShort() {
        return personDao.findAllOrdered()
                .stream()
                .map(PersonDto::toShort)
                .toList();
    }

    /**
     * Поиск владельцев по подстроке имени (или топ-N, если запрос пустой).
     *
     * @param q     запрос (подстрока); если пустой — возвращает top-N
     * @param limit максимальное количество результатов
     * @return список кратких DTO
     */
    public List<PersonDto> searchShort(String q, int limit) {
        var list = (q == null || q.isBlank())
                ? personDao.findTop(limit)
                : personDao.searchByName(q, limit);
        return list.stream().map(PersonDto::toShort).toList();
    }

    /**
     * Подсчёт количества ТС у владельца.
     *
     * @param personId ID владельца
     * @return число ТС
     */
    public long countVehiclesOf(Long personId) {
        return vehicleDao.countByOwnerId(personId);
    }

    /**
     * Удалить владельца с учётом связанных ТС: при необходимости — переназначить их другому владельцу.
     *
     * <h4>Алгоритм</h4>
     * <ol>
     *   <li>Проверить существование владельца.</li>
     *   <li>Посчитать число связанных ТС.</li>
     *   <li>Если ТС есть, но {@code reassignTo} не задан — вернуть 409 CONFLICT с полезной нагрузкой
     *       {@code {code: "FK_CONSTRAINT", message, refCount}} (для UX диалога переназначения).</li>
     *   <li>Если ТС есть и {@code reassignTo} задан — проверить валидность, запретить self-assign, выполнить переназначение.</li>
     *   <li>Удалить владельца.</li>
     * </ol>
     *
     * <p><b>Транзакционность:</b> операция атомарна — либо переназначение и удаление успешны, либо откат.</p>
     *
     * @param personId  кого удаляем
     * @param reassignTo на кого переносим ТС (может быть {@code null})
     * @throws WebApplicationException 404 — если удаляемый владелец не найден<br>
     *                                  409 — если есть связанные ТС и не задан {@code reassignTo}<br>
     *                                  400 — если {@code reassignTo} равен {@code personId} или целевой владелец не существует
     */
    @Transactional
    public void deletePerson(Long personId, Long reassignTo) {
        Person victim = personDao.findById(personId)
                .orElseThrow(() -> new WebApplicationException(
                        "Person not found: " + personId, Response.Status.NOT_FOUND));

        // 1) Считаем привязанные ТС
        long refCount = vehicleDao.countByOwnerId(personId);

        // 2) Если ТС есть, но reassignTo не передан — отдать 409, чтобы фронтенд показал выбор нового владельца
        if (refCount > 0 && reassignTo == null) {
            // Вернём JSON вида { code:"FK_CONSTRAINT", message:"...", refCount:N }
            var entity = Map.of(
                    "code", "FK_CONSTRAINT",
                    "message", "Нельзя удалить — есть связанные транспортные средства",
                    "refCount", refCount
            );
            throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT).entity(entity).build()
            );
        }

        // 3) Если требуется переназначение (есть ТС и reassignTo задан)
        if (refCount > 0 && reassignTo != null) {
            if (Objects.equals(personId, reassignTo)) {
                throw new WebApplicationException(
                        "Нельзя переназначать на самого себя", Response.Status.BAD_REQUEST);
            }
            if (!personDao.existsById(reassignTo)) {
                throw new WebApplicationException(
                        "Целевой владелец (reassignTo) не найден: " + reassignTo,
                        Response.Status.BAD_REQUEST);
            }
            vehicleDao.reassignOwner(personId, reassignTo); // переназначаем все ТС
        }

        // 4) Теперь удаляем владельца (если ТС не было — просто удалится)
        personDao.deleteById(personId);
    }

    /**
     * Защищённое удаление: удалить владельца только если к нему не привязаны ТС.
     *
     * @param id идентификатор владельца
     * @throws WebApplicationException 409 — если есть связанные ТС (возвращается полезная нагрузка c {@code code, refCount})
     */
    public void deleteGuarded(Long id) {
        long refs = vehicleDao.countByOwnerId(id);
        if (refs > 0) {
            throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT)
                            .entity(Map.of(
                                    "message", "Нельзя удалить владельца: к нему привязано " + refs + " транспортных средств",
                                    "code", "FK_CONSTRAINT",
                                    "refCount", refs
                            ))
                            .build()
            );
        }
        personDao.deleteById(id);
    }
}
