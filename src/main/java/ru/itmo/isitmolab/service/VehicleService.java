package ru.itmo.isitmolab.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ru.itmo.isitmolab.dao.CoordinatesDao;
import ru.itmo.isitmolab.dao.VehicleDao;
import ru.itmo.isitmolab.dto.GridTableRequest;
import ru.itmo.isitmolab.dto.GridTableResponse;
import ru.itmo.isitmolab.dto.VehicleDto;
import ru.itmo.isitmolab.exception.VehicleNameNotUniqueException;
import ru.itmo.isitmolab.model.Coordinates;
import ru.itmo.isitmolab.model.Vehicle;
import ru.itmo.isitmolab.ws.VehicleWsService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class VehicleService {

    @Inject
    private VehicleDao dao;
    @Inject
    private VehicleWsService wsHub;
    @Inject
    private CoordinatesDao coordinatesDao;

    @Transactional
    public Long createNewVehicle(VehicleDto dto) {
        // ОГРАНИЧЕНИЕ
        checkUniqueVehicleName(dto.getName(), null);

        Coordinates coords = resolveCoordinatesForDto(dto);

        Vehicle v = VehicleDto.toEntity(dto, null);
        v.setCoordinates(coords);

        dao.save(v);
        wsHub.broadcastText("refresh");
        return v.getId();
    }

    @Transactional
    public void updateVehicle(Long id, VehicleDto dto) {
        Vehicle current = dao.findById(id).orElseThrow(() -> new WebApplicationException("Vehicle not found: " + id, Response.Status.NOT_FOUND));

        // ОГРАНИЧЕНИЕ
        if (dto.getName() != null && !dto.getName().equals(current.getName()))
            checkUniqueVehicleName(dto.getName(), id);


        if (dto.getCoordinatesId() != null || (dto.getCoordinatesX() != null && dto.getCoordinatesY() != null)) {
            Coordinates coords = resolveCoordinatesForDto(dto);
            current.setCoordinates(coords);
        }

        VehicleDto.toEntity(dto, current);

        dao.save(current);
        wsHub.broadcastText("refresh");
    }

    public VehicleDto getVehicleById(Long id) {
        Vehicle v = dao.findById(id)
                .orElseThrow(() -> new WebApplicationException(
                        "Vehicle not found: " + id, Response.Status.NOT_FOUND));
        return VehicleDto.toDto(v);
    }

    @Transactional
    public void deleteVehicleById(Long id) {
        try {
            dao.findById(id).orElseThrow(() -> new WebApplicationException("Vehicle not found: " + id, Response.Status.NOT_FOUND));
            dao.deleteById(id);
        } catch (OptimisticLockException e) {
            throw new WebApplicationException("The vehicle was already deleted by another user.", Response.Status.CONFLICT);
        }
    }

    public GridTableResponse<VehicleDto> queryVehiclesTable(GridTableRequest req) {
        List<Vehicle> rows = dao.findPageByGrid(req);
        long total = dao.countByGrid(req);
        List<VehicleDto> dtos = rows.stream()
                .map(VehicleDto::toDto)
                .toList();

        return new GridTableResponse<>(dtos, (int) total);
    }

    public Coordinates resolveCoordinatesForDto(VehicleDto dto) {
        if (dto.getCoordinatesId() != null) {
            return coordinatesDao.findById(dto.getCoordinatesId())
                    .orElseThrow(() -> new WebApplicationException(
                            Response.status(Response.Status.BAD_REQUEST)
                                    .type(MediaType.APPLICATION_JSON_TYPE)
                                    .entity(Map.of("message", "Не найдены координаты с id " + dto.getCoordinatesId()))
                                    .build()
                    ));
        }
        if (dto.getCoordinatesX() != null && dto.getCoordinatesY() != null) { // Если id нет, но сырые x и y
            return coordinatesDao.findOrCreateByXY(dto.getCoordinatesX(), dto.getCoordinatesY());
        }
        throw new WebApplicationException("coordinatesId или (coordinatesX, coordinatesY) — обязательны",
                Response.Status.BAD_REQUEST);
    }

    @Transactional
    public void checkUniqueVehicleName(String name, Long excludeId) {
        if (name == null || name.isBlank()) return;

        Optional<Vehicle> existing =
                (excludeId == null)
                        ? dao.findByName(name)
                        : dao.findByNameAndIdNot(name, excludeId);

        if (existing.isPresent())
            throw new VehicleNameNotUniqueException(name);
    }

}
