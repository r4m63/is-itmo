package ru.itmo.isitmolab.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import ru.itmo.isitmolab.dao.CoordinatesDao;
import ru.itmo.isitmolab.dao.VehicleDao;
import ru.itmo.isitmolab.dto.GridTableRequest;
import ru.itmo.isitmolab.dto.GridTableResponse;
import ru.itmo.isitmolab.dto.CoordinatesDto;
import ru.itmo.isitmolab.model.Coordinates;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@ApplicationScoped
public class CoordinatesService {

    @Inject
    CoordinatesDao coordinatesDao;

    @Inject
    VehicleDao vehicleDao;

    public GridTableResponse<CoordinatesDto> query(GridTableRequest req) {
        List<Coordinates> rows = coordinatesDao.findPageByGrid(req);
        long total = coordinatesDao.countByGrid(req);

        Map<Long, Integer> counts = coordinatesDao.countVehiclesForCoordinatesIds(
                rows.stream().map(Coordinates::getId).toList()
        );

        List<CoordinatesDto> dtos = rows.stream()
                .map(c -> CoordinatesDto.toDto(c, counts.getOrDefault(c.getId(), 0)))
                .toList();
        return new GridTableResponse<>(dtos, (int) total);
    }

    public CoordinatesDto getOne(Long id) {
        Coordinates c = coordinatesDao.findById(id)
                .orElseThrow(() -> new WebApplicationException("Coordinates not found: " + id, Response.Status.NOT_FOUND));
        int cnt = coordinatesDao.countVehiclesForCoordinatesId(id);
        return CoordinatesDto.toDto(c, cnt);
    }


    @Transactional
    public Long create(CoordinatesDto dto) {
        if (dto.getX() == null || dto.getY() == null) {
            throw new WebApplicationException("x и y обязательны", Response.Status.BAD_REQUEST);
        }
        Coordinates c = coordinatesDao.findOrCreateByXY(dto.getX(), dto.getY());
        return c.getId();
    }

    @Transactional
    public void update(Long id, CoordinatesDto dto) {
        Coordinates c = coordinatesDao.findById(id)
                .orElseThrow(() -> new WebApplicationException("Coordinates not found: " + id, Response.Status.NOT_FOUND));

        if (dto.getX() != null) c.setX(dto.getX());
        if (dto.getY() != null) c.setY(dto.getY());

        coordinatesDao.save(c);
    }

    @Transactional
    public void delete(Long id) {
        coordinatesDao.deleteById(id);
    }

    public List<CoordinatesDto> searchShort(String q, int limit) {
        return coordinatesDao.search(q, limit).stream()
                .map(CoordinatesDto::toShort)
                .toList();
    }

    public long countVehiclesOf(Long coordinatesId) {
        return vehicleDao.countByCoordinatesId(coordinatesId);
    }

    @Transactional
    public void deleteCoordinates(Long coordinatesId, Long reassignTo) {
        Coordinates victim = coordinatesDao.findById(coordinatesId)
                .orElseThrow(() -> new WebApplicationException(
                        "Coordinates not found: " + coordinatesId, Response.Status.NOT_FOUND));

        long refCount = vehicleDao.countByCoordinatesId(coordinatesId);

        if (refCount > 0 && reassignTo == null) {
            var entity = Map.of(
                    "code", "FK_CONSTRAINT",
                    "message", "Нельзя удалить — есть связанные транспортные средства",
                    "refCount", refCount
            );
            throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT).entity(entity).build()
            );
        }

        if (refCount > 0 && reassignTo != null) {
            if (Objects.equals(coordinatesId, reassignTo)) {
                throw new WebApplicationException("Нельзя переназначать на те же координаты", Response.Status.BAD_REQUEST);
            }
            if (!coordinatesDao.existsById(reassignTo)) {
                throw new WebApplicationException("Целевые координаты (reassignTo) не найдены: " + reassignTo,
                        Response.Status.BAD_REQUEST);
            }
            vehicleDao.reassignCoordinates(coordinatesId, reassignTo);
        }

        coordinatesDao.deleteById(coordinatesId);
    }
}
