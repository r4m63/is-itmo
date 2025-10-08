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

    public GridTableResponse<PersonDto> query(GridTableRequest req) {
        List<Person> rows = personDao.findPageByGrid(req);
        long total = personDao.countByGrid(req);

        // считаем qty без ленивой инициализации коллекции
        Map<Long, Integer> counts = personDao.countVehiclesForPersonIds(
                rows.stream().map(Person::getId).toList()
        );

        List<PersonDto> dtos = rows.stream()
                .map(p -> PersonDto.toDto(p, counts.getOrDefault(p.getId(), 0)))
                .toList();
        return new GridTableResponse<>(dtos, (int) total);
    }

    public PersonDto getOne(Long id) {
        Person p = personDao.findById(id)
                .orElseThrow(() -> new WebApplicationException("Person not found: " + id, Response.Status.NOT_FOUND));

        int cnt = personDao.countVehiclesForPersonId(id);
        return PersonDto.toDto(p, cnt);
    }

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

    public void update(Long id, PersonDto dto, HttpServletRequest req) {
        Person p = personDao.findById(id)
                .orElseThrow(() -> new WebApplicationException("Person not found: " + id, Response.Status.NOT_FOUND));

        // правим только fullName
        PersonDto.apply(dto, p);
        personDao.save(p);
    }

    public void delete(Long id) {
        personDao.deleteById(id);
    }

    public List<PersonDto> listAllShort() {
        return personDao.findAllOrdered()
                .stream()
                .map(PersonDto::toShort)
                .toList();
    }

    public List<PersonDto> searchShort(String q, int limit) {
        var list = (q == null || q.isBlank())
                ? personDao.findTop(limit)
                : personDao.searchByName(q, limit);
        return list.stream().map(PersonDto::toShort).toList();
    }

    public long countVehiclesOf(Long personId) {
        return vehicleDao.countByOwnerId(personId);
    }

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



    /** Удалить только если нет привязанных ТС, иначе вернуть 409 */
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
