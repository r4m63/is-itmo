package ru.itmo.isitmolab.exception;

import lombok.Getter;
import ru.itmo.isitmolab.dto.ValidationErrors;

import java.util.List;

@Getter
public class RequestValidationException extends BusinessException {

    private final List<ValidationErrors.FieldError> errors;

    public RequestValidationException(String message, List<ValidationErrors.FieldError> errors) {
        super("VALIDATION", message);
        this.errors = errors;
    }
}

