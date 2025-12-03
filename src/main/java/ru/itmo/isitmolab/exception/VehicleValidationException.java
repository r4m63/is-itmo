package ru.itmo.isitmolab.exception;

import lombok.Getter;
import ru.itmo.isitmolab.dto.VehicleImportErrors;

import java.util.List;

@Getter
public class VehicleValidationException extends BusinessException {

    private final List<VehicleImportErrors.RowError> errors;

    public VehicleValidationException(String message, List<VehicleImportErrors.RowError> errors) {
        super("VEHICLE_IMPORT_VALIDATION_ERROR", message);
        this.errors = errors;
    }

}

