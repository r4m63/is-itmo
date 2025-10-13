package ru.itmo.isitmolab.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ru.itmo.isitmolab.dao.AdminDao;
import ru.itmo.isitmolab.dao.CoordinatesDao;
import ru.itmo.isitmolab.dao.VehicleDao;
import ru.itmo.isitmolab.dto.GridTableRequest;
import ru.itmo.isitmolab.dto.GridTableResponse;
import ru.itmo.isitmolab.dto.VehicleDto;
import ru.itmo.isitmolab.model.Admin;
import ru.itmo.isitmolab.model.Coordinates;
import ru.itmo.isitmolab.model.Vehicle;
import ru.itmo.isitmolab.ws.VehicleWsHub;

import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private CoordinatesDao coordinatesDao;

    public Long createNewVehicle(VehicleDto dto, HttpServletRequest req) {
        Long adminId = sessionService.getCurrentUserId(req);
        Admin admin = adminDao.findById(adminId)
                .orElseThrow(() -> new WebApplicationException(
                        "Admin not found: " + adminId, Response.Status.UNAUTHORIZED));

        Coordinates coords = resolveCoordinatesForDto(dto);

        Vehicle v = VehicleDto.toEntity(dto, null);
        v.setAdmin(admin);
        v.setCoordinates(coords);

        dao.save(v);
        wsHub.broadcastText("refresh");
        return v.getId();
    }

    public void updateVehicle(Long id, VehicleDto dto) {
        Vehicle current = dao.findById(id)
                .orElseThrow(() -> new WebApplicationException(
                        "Vehicle not found: " + id, Response.Status.NOT_FOUND));

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

    public void deleteVehicleById(Long id) {
        if (!dao.existsById(id)) {
            throw new WebApplicationException(
                    "Vehicle not found: " + id, Response.Status.NOT_FOUND);
        }
        dao.deleteById(id);
        wsHub.broadcastText("refresh");
    }

    public List<VehicleDto> getAllVehicles() {
        return dao.findAll().stream().map(VehicleDto::toDto).toList();
    }

    public GridTableResponse<VehicleDto> queryTableGridFilters(GridTableRequest req) {
        List<Vehicle> rows = dao.findPageByGrid(req);
        long total = dao.countByGrid(req);
        List<VehicleDto> dtos = rows.stream()
                .map(VehicleDto::toDto)
                .toList();

        return new GridTableResponse<>(dtos, (int) total);
    }

    public List<Vehicle> findByCoordinates(Long coordinatesId) {
        return dao.findByCoordinatesId(coordinatesId);
    }

    @Transactional
    public int reassignCoordinatesBulk(Long fromCoordinatesId, Long toCoordinatesId) {
        if (Objects.equals(fromCoordinatesId, toCoordinatesId)) {
            throw new WebApplicationException("Нельзя переназначать на те же координаты", Response.Status.BAD_REQUEST);
        }
        if (toCoordinatesId == null || !coordinatesDao.existsById(toCoordinatesId)) {
            throw new WebApplicationException("Целевая точка координат не найдена: " + toCoordinatesId,
                    Response.Status.BAD_REQUEST);
        }
        return dao.reassignCoordinates(fromCoordinatesId, toCoordinatesId);
    }

    private Coordinates resolveCoordinatesForDto(VehicleDto dto) {
        if (dto.getCoordinatesId() != null) {
            return coordinatesDao.findById(dto.getCoordinatesId())
                    .orElseThrow(() -> new WebApplicationException(
                            Response.status(Response.Status.BAD_REQUEST)
                                    .type(MediaType.APPLICATION_JSON_TYPE)
                                    .entity(Map.of("message", "Не найдены координаты с id " + dto.getCoordinatesId()))
                                    .build()
                    ));
        }
        if (dto.getCoordinatesX() != null && dto.getCoordinatesY() != null) {
            return coordinatesDao.findOrCreateByXY(dto.getCoordinatesX(), dto.getCoordinatesY());
        }
        throw new WebApplicationException("coordinatesId или (coordinatesX, coordinatesY) — обязательны",
                Response.Status.BAD_REQUEST);
    }
}
