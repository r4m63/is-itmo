//package ru.itmo.isitmolab.exception.mapper;
//
//import jakarta.persistence.OptimisticLockException;
//import jakarta.ws.rs.core.MediaType;
//import jakarta.ws.rs.core.Response;
//import jakarta.ws.rs.ext.ExceptionMapper;
//import jakarta.ws.rs.ext.Provider;
//import java.sql.SQLException;
//import java.util.ArrayDeque;
//import java.util.LinkedHashMap;
//import java.util.Map;
//
//@Provider
//public class OptimisticLockExceptionMapper implements ExceptionMapper<OptimisticLockException> {
//
//    @Override
//    public Response toResponse(OptimisticLockException ex) {
//        if (hasSqlState(ex, "40001")) {
//            Map<String, Object> body = new LinkedHashMap<>();
//            body.put("error", "SERIALIZATION_CONFLICT");
//            body.put("message", "Конкурентный доступ, повторите запрос");
//
//            return Response.status(Response.Status.CONFLICT)
//                    .type(MediaType.APPLICATION_JSON_TYPE)
//                    .entity(body)
//                    .build();
//        }
//
//        Map<String, Object> body = new LinkedHashMap<>();
//        body.put("error", "OPTIMISTIC_LOCK");
//        body.put("message", "Сущность была изменена или удалена другим пользователем");
//
//        return Response.status(Response.Status.CONFLICT)
//                .type(MediaType.APPLICATION_JSON_TYPE)
//                .entity(body)
//                .build();
//    }
//
//    private boolean hasSqlState(Throwable ex, String sqlState) {
//        if (ex == null || sqlState == null) return false;
//
//        ArrayDeque<Throwable> queue = new ArrayDeque<>();
//        queue.add(ex);
//
//        while (!queue.isEmpty()) {
//            Throwable cur = queue.pop();
//
//            if (cur instanceof SQLException sql && sqlState.equals(sql.getSQLState())) {
//                return true;
//            }
//            for (Throwable suppressed : cur.getSuppressed()) {
//                queue.add(suppressed);
//            }
//            Throwable cause = cur.getCause();
//            if (cause != null && cause != cur) {
//                queue.add(cause);
//            }
//        }
//        return false;
//    }
//}
