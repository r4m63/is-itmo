package ru.itmo.isitmolab.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import ru.itmo.isitmolab.dao.VehicleImportOperationDao;
import ru.itmo.isitmolab.dto.VehicleImportErrors;
import ru.itmo.isitmolab.dto.VehicleImportHistoryItemDto;
import ru.itmo.isitmolab.dto.VehicleImportItemDto;
import ru.itmo.isitmolab.exception.VehicleValidationException;
import ru.itmo.isitmolab.model.VehicleImportOperation;
import ru.itmo.isitmolab.util.BeanValidation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class VehicleImportService {

    private static final String MINIO_IMPORT_PREFIX = "imports";

    @Inject
    private VehicleImportOperationDao importOperationDao;

    @Inject
    private VehicleImportOperationLogger opLogger;

    @Inject
    private VehicleImportTxService txService;

    public void importVehicles(List<VehicleImportItemDto> items,
                               byte[] rawFile,
                               String originalFileName,
                               String contentType) {

        final String opUuid = UUID.randomUUID().toString(); // ключ файла в MinIO + имя
        final String ext = guessExt(originalFileName, contentType, ".json"); // расширение файла
        final String safeName = sanitizeFileName(originalFileName, "import-" + opUuid + ext);
        final String ct = (contentType == null || contentType.isBlank())
                ? "application/octet-stream"
                : contentType;
        final byte[] fileBytes = (rawFile == null) ? new byte[0] : rawFile;
        final long size = fileBytes.length;
        final String finalKey = MINIO_IMPORT_PREFIX + "/" + opUuid + ext; // ключ

        // log started
        final Long opId = opLogger.createStarted(safeName, ct, size);

        // validation before transaction minio + db
        List<VehicleImportErrors.RowError> errors = validateItems(items);
        if (!errors.isEmpty()) {
            // при ошибке файл не сохраняется
            opLogger.markFailure(opId, 0, null, safeName, ct, size);
            throw new VehicleValidationException("Validation failed", errors);
        }

        // транзакция
        txService.importVehiclesTx(opId, items, fileBytes, finalKey, safeName, ct, size);
    }

    private List<VehicleImportErrors.RowError> validateItems(List<VehicleImportItemDto> items) {
        List<VehicleImportErrors.RowError> errors = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            errors.add(new VehicleImportErrors.RowError(1, "items", "Файл не содержит данных для импорта"));
            return errors;
        }

        for (int i = 0; i < items.size(); i++) {
            VehicleImportItemDto item = items.get(i);
            if (item == null) {
                errors.add(new VehicleImportErrors.RowError(i + 1, "item", "Пустая запись"));
                continue;
            }

            Set<ConstraintViolation<VehicleImportItemDto>> violations = BeanValidation.validate(item);
            for (ConstraintViolation<VehicleImportItemDto> v : violations) {
                errors.add(new VehicleImportErrors.RowError(
                        i + 1,
                        v.getPropertyPath().toString(),
                        v.getMessage()
                ));
            }
        }
        return errors;
    }

    private String sanitizeFileName(String original, String fallback) {
        if (original == null || original.isBlank()) return fallback;
        String s = original
                .replace("\"", "")
                .replace("\\", "_")
                .replace("/", "_")
                .replace("\n", "")
                .replace("\r", "")
                .trim();
        return s.isBlank() ? fallback : s;
    }

    private String guessExt(String originalFileName, String contentType, String defaultExt) {
        if (originalFileName != null) {
            int dot = originalFileName.lastIndexOf('.');
            if (dot >= 0 && dot < originalFileName.length() - 1) {
                String ext = originalFileName.substring(dot);
                if (ext.length() <= 10) return ext;
            }
        }
        return defaultExt;
    }

    public List<VehicleImportHistoryItemDto> getHistoryForAdmin(int limit) {
        return importOperationDao.findLastForAdmin(limit)
                .stream()
                .map(VehicleImportHistoryItemDto::toDto)
                .collect(Collectors.toList());
    }

    public VehicleImportOperation getOperationOrThrow(Long id) {
        return importOperationDao.findById(id)
                .orElseThrow(() -> new jakarta.ws.rs.WebApplicationException(
                        "Import operation not found: " + id,
                        jakarta.ws.rs.core.Response.Status.NOT_FOUND
                ));
    }
}