package ru.itmo.isitmolab.exception.mapper;

import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.RollbackException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.sql.SQLException;
import java.util.Map;

@Provider
public class RollbackExceptionMapper implements ExceptionMapper<RollbackException> {

    @Override
    public Response toResponse(RollbackException ex) {

        OptimisticLockException optimisticLock = null;
        SQLException sqlException = null;

        // Проходим всю цепочку причин и собираем, что нас интересует
        Throwable cause = ex;
        while (cause != null) {
            if (optimisticLock == null && cause instanceof OptimisticLockException) {
                optimisticLock = (OptimisticLockException) cause;
            }
            if (sqlException == null && cause instanceof SQLException) {
                sqlException = (SQLException) cause;
            }
            cause = cause.getCause();
        }

        // 1. Оптимистичная блокировка
        if (optimisticLock != null) {
            var body = Map.of(
                    "error", "OPTIMISTIC_LOCK",
                    "message", "Сущность была изменена или удалена другим пользователем"
            );
            return Response.status(Response.Status.CONFLICT)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(body)
                    .build();
        }

        // 2. SERIALIZABLE-конфликт в PostgreSQL (SQLState 40001)
        if (sqlException != null && "40001".equals(sqlException.getSQLState())) {
            var body = Map.of(
                    "error", "SERIALIZATION_CONFLICT",
                    "message", "Конкурентный доступ, повторите запрос"
            );
            return Response.status(Response.Status.CONFLICT)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .entity(body)
                    .build();
        }

        // 3. Любой другой rollback транзакции
        var body = Map.of(
                "error", "TRANSACTION_ROLLBACK",
                "message", "Ошибка при выполнении транзакции"
        );
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)  // 500
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(body)
                .build();
    }
}
