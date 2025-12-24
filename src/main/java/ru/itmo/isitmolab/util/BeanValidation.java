package ru.itmo.isitmolab.util;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import ru.itmo.isitmolab.dto.ValidationErrors;
import ru.itmo.isitmolab.exception.RequestValidationException;

import java.util.List;
import java.util.Set;

public final class BeanValidation {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    private BeanValidation() {
    }

    public static <T> Set<ConstraintViolation<T>> validate(T obj) {
        if (obj == null) {
            return Set.of();
        }
        return VALIDATOR.validate(obj);
    }

    public static <T> void validateOrThrow(T obj) {
        Set<ConstraintViolation<T>> violations = validate(obj);
        if (violations.isEmpty()) {
            return;
        }

        List<ValidationErrors.FieldError> errors = violations.stream()
                .map(v -> ValidationErrors.FieldError.builder()
                        .field(v.getPropertyPath() == null ? null : v.getPropertyPath().toString())
                        .message(v.getMessage())
                        .build())
                .toList();

        throw new RequestValidationException("Validation failed", errors);
    }
}

