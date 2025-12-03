package ru.itmo.isitmolab.exception.mapper;

import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.RollbackException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

@Provider
public class RollbackExceptionMapper implements ExceptionMapper<RollbackException> {

    @Override
    public Response toResponse(RollbackException ex) {

        // OptimisticLockException
        OptimisticLockException opt = findCause(ex, OptimisticLockException.class);
        if (opt != null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "OPTIMISTIC_LOCK");
            body.put("message", "Сущность была изменена или удалена другим пользователем");

            return Response.status(Response.Status.CONFLICT)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(body)
                    .build();
        }

        // SERIALIZABLE-конфликт: SQLException с SQLState=40001
        SQLException sqlEx = findCause(ex, SQLException.class);
        if (sqlEx != null && "40001".equals(sqlEx.getSQLState())) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "SERIALIZATION_CONFLICT");
            body.put("message", "Конкурентный доступ, повторите запрос");

            return Response.status(Response.Status.CONFLICT)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(body)
                    .build();
        }

        // Любой другой rollback транзакции
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "TRANSACTION_ROLLBACK");
        body.put("message", "Ошибка при выполнении транзакции");

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(body)
                .build();
    }

    private <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
        Throwable cause = ex;
        while (cause != null) {
            if (type.isInstance(cause)) {
                return type.cast(cause);
            }
            cause = cause.getCause();
        }
        return null;
    }
}
