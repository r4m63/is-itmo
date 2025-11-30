package ru.itmo.isitmolab.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ru.itmo.isitmolab.dao.CoordinatesDao;
import ru.itmo.isitmolab.dao.VehicleDao;
import ru.itmo.isitmolab.dao.VehicleImportOperationDao;
import ru.itmo.isitmolab.dto.*;
import ru.itmo.isitmolab.model.Coordinates;
import ru.itmo.isitmolab.model.Vehicle;
import ru.itmo.isitmolab.model.VehicleImportOperation;
import ru.itmo.isitmolab.ws.VehicleWsService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class VehicleService {

    @Inject
    private VehicleDao dao;

    @Inject
    private VehicleWsService wsHub;

    @Inject
    private CoordinatesDao coordinatesDao;

    @Inject
    private VehicleImportOperationDao importOperationDao;


    @Transactional
    public Long createNewVehicle(VehicleDto dto) {

        // === БИЗНЕС-ОГРАНИЧЕНИЕ: уникальность имени ТС при создании ===
        ensureVehicleNameUnique(dto.getName(), null);

        Coordinates coords = resolveCoordinatesForDto(dto);

        Vehicle v = VehicleDto.toEntity(dto, null);
        v.setCoordinates(coords);

        dao.save(v);
        wsHub.broadcastText("refresh");
        return v.getId();
    }

    @Transactional
    public void updateVehicle(Long id, VehicleDto dto) {
        Vehicle current = dao.findById(id)
                .orElseThrow(() -> new WebApplicationException(
                        "Vehicle not found: " + id, Response.Status.NOT_FOUND));

        // === БИЗНЕС-ОГРАНИЧЕНИЕ: уникальность имени ТС при обновлении ===
        if (dto.getName() != null && !dto.getName().equals(current.getName())) {
            ensureVehicleNameUnique(dto.getName(), id);
        }

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
        if (!dao.existsById(id)) {
            throw new WebApplicationException(
                    "Vehicle not found: " + id, Response.Status.NOT_FOUND);
        }
        dao.deleteById(id);
        wsHub.broadcastText("refresh");
    }

    public GridTableResponse<VehicleDto> queryVehiclesTable(GridTableRequest req) {
        List<Vehicle> rows = dao.findPageByGrid(req);
        long total = dao.countByGrid(req);
        List<VehicleDto> dtos = rows.stream()
                .map(VehicleDto::toDto)
                .toList();

        return new GridTableResponse<>(dtos, (int) total);
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
        if (dto.getCoordinatesX() != null && dto.getCoordinatesY() != null) { // Если id нет, но сырые x и y
            return coordinatesDao.findOrCreateByXY(dto.getCoordinatesX(), dto.getCoordinatesY());
        }
        throw new WebApplicationException("coordinatesId или (coordinatesX, coordinatesY) — обязательны",
                Response.Status.BAD_REQUEST);
    }

    @Transactional
    public void importVehicles(List<VehicleImportItemDto> items) {
        for (VehicleImportItemDto item : items) {
            VehicleDto dto = VehicleImportItemDto.toEntity(item);

            // === БИЗНЕС-ОГРАНИЧЕНИЕ: уникальность имени ТС и при импорте ===
            // Здесь можно решить: либо запрещать дубликаты, либо скипать/логировать.
            // Сейчас — жёсткий запрет: если имя уже есть, кидаем 409.
            ensureVehicleNameUnique(dto.getName(), null);

            Coordinates coords = resolveCoordinatesForDto(dto);
            Vehicle v = VehicleDto.toEntity(dto, null);
            v.setCoordinates(coords);
            dao.save(v);
        }

        VehicleImportOperation op = new VehicleImportOperation();
        op.setStatus(Boolean.TRUE);
        op.setImportedCount(items.size());

        importOperationDao.save(op);

        wsHub.broadcastText("refresh");
    }

    public List<VehicleImportHistoryItemDto> getHistoryForAdmin(int limit) {
        return importOperationDao.findLastForAdmin(limit)
                .stream()
                .map(VehicleImportHistoryItemDto::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Бизнес-ограничение: имя транспортного средства (Vehicle.name)
     * должно быть уникальным в системе (без UNIQUE в БД).
     *
     * @param name      имя ТС
     * @param excludeId id ТС, которое сейчас обновляем (null для создания/импорта)
     */
    private void ensureVehicleNameUnique(String name, Long excludeId) {
        if (name == null || name.isBlank()) {
            // Формальная проверка NotBlank делается Bean Validation,
            // здесь только уникальность.
            return;
        }

        boolean exists;

        if (excludeId == null) {
            exists = dao.existsByName(name);
        } else {
            exists = dao.existsByNameAndIdNot(name, excludeId);
        }

        if (exists) {
            var body = Map.of(
                    "error", "VEHICLE_NAME_NOT_UNIQUE",
                    "message", "Транспортное средство с именем '" + name + "' уже существует"
            );

            throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT)
                            .type(MediaType.APPLICATION_JSON_TYPE)
                            .entity(body)
                            .build()
            );
        }
    }

}
