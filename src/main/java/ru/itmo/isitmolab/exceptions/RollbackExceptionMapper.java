package ru.itmo.isitmolab.exceptions;

import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.RollbackException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class RollbackExceptionMapper implements ExceptionMapper<RollbackException> {
    // jmeter scenario2 DELETE SAME

    @Override
    public Response toResponse(RollbackException ex) {
        Throwable cause = ex;
        OptimisticLockException optLock = null;

        while (cause != null) {
            if (cause instanceof OptimisticLockException) {
                optLock = (OptimisticLockException) cause;
                break;
            }
            cause = cause.getCause();
        }

        if (optLock != null) {
            var body = Map.of(
                    "error", "OPTIMISTIC_LOCK",
                    "message", "Сущность была изменена или удалена другим пользователем"
            );
            return Response.status(Response.Status.CONFLICT)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(body)
                    .build();
        }

        var body = Map.of(
                "error", "TRANSACTION_ROLLBACK",
                "message", "Ошибка при выполнении транзакции"
        );
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(body)
                .build();
    }
}
