package ru.itmo.isitmolab.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import ru.itmo.isitmolab.dao.VehicleImportOperationDao;
import ru.itmo.isitmolab.model.VehicleImportOperation;

import java.util.Optional;

@ApplicationScoped
public class VehicleImportOperationLogger {

    @Inject
    private VehicleImportOperationDao importOperationDao;

    /**
     * Создать запись об импорте
     * fileObjectKey на этом этапе может быть null (если MinIO упадёт на put)
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Long createStarted(String fileName, String contentType, long size) {
        VehicleImportOperation op = new VehicleImportOperation();
        op.setStatus(false); // по умолчанию "неуспешно", на успехе обновим на true
        op.setImportedCount(0);
        op.setFileObjectKey(null);
        op.setFileName(fileName);
        op.setFileContentType(contentType);
        op.setFileSize(size);

        importOperationDao.save(op);
        return op.getId();
    }

    /** Финализировать успех: status=true, count, finalKey */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void markSuccess(Long opId, int importedCount, String finalKey, String fileName, String contentType, long size) {
        VehicleImportOperation op = mustGet(opId);
        op.setStatus(true);
        op.setImportedCount(importedCount);
        op.setFileObjectKey(finalKey);
        op.setFileName(fileName);
        op.setFileContentType(contentType);
        op.setFileSize(size);
        importOperationDao.save(op);
    }

    /** Финализировать провал: status=false, count, key */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void markFailure(Long opId, int importedCount, String keyToStore, String fileName, String contentType, long size) {
        VehicleImportOperation op = mustGet(opId);
        op.setStatus(false);
        op.setImportedCount(importedCount);
        op.setFileObjectKey(keyToStore);
        op.setFileName(fileName);
        op.setFileContentType(contentType);
        op.setFileSize(size);
        importOperationDao.save(op);
    }

    private VehicleImportOperation mustGet(Long opId) {
        Optional<VehicleImportOperation> found = importOperationDao.findById(opId);
        return found.orElseThrow(() -> new IllegalStateException("Import operation not found: " + opId));
    }
}