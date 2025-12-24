package ru.itmo.isitmolab.exception.mapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import ru.itmo.isitmolab.dto.VehicleImportErrors;
import ru.itmo.isitmolab.dto.ValidationErrors;
import ru.itmo.isitmolab.exception.BusinessException;
import ru.itmo.isitmolab.exception.RequestValidationException;
import ru.itmo.isitmolab.exception.VehicleNameNotUniqueException;
import ru.itmo.isitmolab.exception.VehicleValidationException;

import java.util.LinkedHashMap;
import java.util.Map;

@Provider
public class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {

    @Override
    public Response toResponse(BusinessException ex) {

        if (ex instanceof RequestValidationException rve) {
            ValidationErrors body = ValidationErrors.builder()
                    .error(rve.getErrorCode())
                    .message(rve.getMessage())
                    .errors(rve.getErrors())
                    .build();

            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(body)
                    .build();
        }

        if (ex instanceof VehicleValidationException vve) {
            VehicleImportErrors body = VehicleImportErrors.builder()
                    .errors(vve.getErrors())
                    .build();

            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(body)
                    .build();
        }

        if (ex instanceof VehicleNameNotUniqueException vnue) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", vnue.getErrorCode());
            body.put("message", vnue.getMessage());
            body.put("vehicleName", vnue.getVehicleName());

            return Response.status(Response.Status.CONFLICT)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(body)
                    .build();
        }


        // Дефолтный вариант для других BusinessException
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getErrorCode());
        body.put("message", ex.getMessage());

        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(body)
                .build();
    }
}
