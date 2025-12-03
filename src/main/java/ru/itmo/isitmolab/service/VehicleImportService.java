package ru.itmo.isitmolab.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import ru.itmo.isitmolab.dao.VehicleDao;
import ru.itmo.isitmolab.dao.VehicleImportOperationDao;
import ru.itmo.isitmolab.dto.VehicleDto;
import ru.itmo.isitmolab.dto.VehicleImportErrors;
import ru.itmo.isitmolab.dto.VehicleImportHistoryItemDto;
import ru.itmo.isitmolab.dto.VehicleImportItemDto;
import ru.itmo.isitmolab.exception.BusinessException;
import ru.itmo.isitmolab.exception.VehicleValidationException;
import ru.itmo.isitmolab.model.Coordinates;
import ru.itmo.isitmolab.model.Vehicle;
import ru.itmo.isitmolab.model.VehicleImportOperation;
import ru.itmo.isitmolab.ws.VehicleWsService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class VehicleImportService {

    @Inject
    private Validator validator;
    @Inject
    private VehicleImportOperationDao importOperationDao;
    @Inject
    private VehicleImportService self;
    @Inject
    private VehicleService vehicleService;
    @Inject
    private VehicleDao dao;
    @Inject
    private VehicleWsService wsHub;


    @Transactional
    public void importVehicles(List<VehicleImportItemDto> items) {

        // VALIDATION
        List<VehicleImportErrors.RowError> validationErrors = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            VehicleImportItemDto item = items.get(i);
            Set<ConstraintViolation<VehicleImportItemDto>> violations = validator.validate(item);

            for (ConstraintViolation<VehicleImportItemDto> violation : violations) {
                validationErrors.add(new VehicleImportErrors.RowError(
                        i + 1,
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ));
            }
        }

        // Если есть ошибки валидации DTO, сразу возвращаем их
        if (!validationErrors.isEmpty()) {
            try {
                self.logImportOperation(false, null);
            } catch (Exception ignored) {
            }
            throw new VehicleValidationException("Validation failed", validationErrors);
        }

        try {
            // IMPORT
            for (VehicleImportItemDto item : items) {
                VehicleDto dto = VehicleImportItemDto.toEntity(item);

                // ОГРАНИЧЕНИЕ
                vehicleService.checkUniqueVehicleName(dto.getName(), null);

                Coordinates coords = vehicleService.resolveCoordinatesForDto(dto);
                Vehicle v = VehicleDto.toEntity(dto, null);
                v.setCoordinates(coords);
                dao.save(v);
            }

            int importedCount = items.size();
            self.logImportOperation(true, importedCount);
            wsHub.broadcastText("refresh");

        } catch (ConstraintViolationException e) {
            // Нарушены ограничения на уровне БД / JPA
            List<VehicleImportErrors.RowError> errors = e.getConstraintViolations().stream()
                    .map(v -> {
                        String path = v.getPropertyPath().toString();

                        // инлайн extractRowNumberFromPath(path)
                        Integer rowNumber = null;
                        int open = path.indexOf('[');
                        if (open != -1) {
                            int close = path.indexOf(']', open + 1);
                            if (close != -1) {
                                String indexStr = path.substring(open + 1, close);
                                try {
                                    int idx = Integer.parseInt(indexStr);
                                    rowNumber = idx + 1; // делаем 1-based, как раньше
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }

                        return new VehicleImportErrors.RowError(
                                rowNumber,
                                path,
                                v.getMessage()
                        );
                    })
                    .collect(Collectors.toList());

            try {
                self.logImportOperation(false, null);
            } catch (Exception ignored) {
            }

            throw new VehicleValidationException("Validation failed", errors);

        } catch (BusinessException e) {
            // уже наш бизнес-эксепшн (например, VehicleNameNotUniqueException)
            try {
                self.logImportOperation(false, null);
            } catch (Exception ignored) {
            }
            throw e; // дальше пойдёт в BusinessExceptionMapper

        } catch (Exception e) {
            // что-то неожиданное — оборачиваем в "технический" бизнес-эксепшн
            try {
                self.logImportOperation(false, null);
            } catch (Exception ignored) {
            }

//            throw new VehicleImportInternalException("Import failed", e);
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void logImportOperation(boolean success, Integer importedCount) {
        VehicleImportOperation op = new VehicleImportOperation();
        op.setStatus(success);
        op.setImportedCount(importedCount);
        importOperationDao.save(op);
    }

    public List<VehicleImportHistoryItemDto> getHistoryForAdmin(int limit) {
        return importOperationDao.findLastForAdmin(limit)
                .stream()
                .map(VehicleImportHistoryItemDto::toDto)
                .collect(Collectors.toList());
    }

}
