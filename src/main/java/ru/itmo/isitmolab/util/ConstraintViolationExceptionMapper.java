//package ru.itmo.isitmolab.util;
//
//import jakarta.inject.Inject;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.transaction.Transactional;
//import jakarta.validation.ConstraintViolation;
//import jakarta.validation.ConstraintViolationException;
//import jakarta.ws.rs.core.Context;
//import jakarta.ws.rs.core.MediaType;
//import jakarta.ws.rs.core.Response;
//import jakarta.ws.rs.ext.ExceptionMapper;
//import jakarta.ws.rs.ext.Provider;
//import ru.itmo.isitmolab.dao.VehicleImportOperationDao;
//import ru.itmo.isitmolab.model.VehicleImportOperation;
//
//import java.util.List;
//import java.util.stream.Collectors;
//
//@Provider
//public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
//
//    @Inject
//    private VehicleImportOperationDao importOperationDao;
//
//    @Context
//    private HttpServletRequest request;
//
//    @Override
//    @Transactional
//    public Response toResponse(ConstraintViolationException exception) {
//
//        // Логируем неуспешную операцию импорта (если это тот самый endpoint)
//        logFailedImportIfNeeded();
//
//        // 2) Формируем тело ответа как раньше
//        List<RowError> errors = exception.getConstraintViolations().stream()
//                .map(this::toRowError)
//                .collect(Collectors.toList());
//
//        ValidationErrorResponse body = new ValidationErrorResponse(
//                "Validation failed",
//                errors
//        );
//
//        return Response.status(Response.Status.BAD_REQUEST)
//                .type(MediaType.APPLICATION_JSON_TYPE)
//                .entity(body)
//                .build();
//    }
//
//    private void logFailedImportIfNeeded() {
//        if (request == null) return;
//
//        String uri = request.getRequestURI(); // /app/api/vehicle/import
//        if (uri == null || !uri.contains("/api/vehicle/import")) {
//            return; // не наш эндпоинт — ничего не логируем
//        }
//
//        VehicleImportOperation op = new VehicleImportOperation();
//        // op.setAdmin(admin); // admin removed
//        op.setStatus(Boolean.FALSE); // неуспешная операция
//        op.setImportedCount(null);   // по ТЗ число только для SUCCESS
//
//        importOperationDao.save(op); // теперь есть активная транзакция
//    }
//
//    private RowError toRowError(ConstraintViolation<?> v) {
//        String path = v.getPropertyPath().toString();
//        Integer rowNumber = extractIndex(path); // может быть null, если индекса нет
//        String field = path;
//        String message = v.getMessage(); // <-- message из аннотаций DTO
//
//        return new RowError(rowNumber, field, message);
//    }
//
//    private Integer extractIndex(String path) {
//        // Наивный парсер индекса из items[3] / arg0[1]
//        int open = path.indexOf('[');
//        int close = path.indexOf(']', open + 1);
//        if (open != -1 && close != -1) {
//            String indexStr = path.substring(open + 1, close);
//            try {
//                int idx = Integer.parseInt(indexStr);
//                return idx + 1; // rowNumber = индекс + 1
//            } catch (NumberFormatException ignored) {
//            }
//        }
//        return null;
//    }
//
//    // === DTO для ответа ===
//
//    public static class ValidationErrorResponse {
//        public String error;
//        public List<RowError> errors;
//
//        public ValidationErrorResponse(String error, List<RowError> errors) {
//            this.error = error;
//            this.errors = errors;
//        }
//    }
//
//    public static class RowError {
//        public Integer rowNumber; // может быть null, если не смогли вычислить
//        public String field;
//        public String message;
//
//        public RowError(Integer rowNumber, String field, String message) {
//            this.rowNumber = rowNumber;
//            this.field = field;
//            this.message = message;
//        }
//    }
//}