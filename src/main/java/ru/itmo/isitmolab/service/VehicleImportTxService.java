package ru.itmo.isitmolab.service;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;
import ru.itmo.isitmolab.dao.VehicleDao;
import ru.itmo.isitmolab.dto.VehicleDto;
import ru.itmo.isitmolab.dto.VehicleImportItemDto;
import ru.itmo.isitmolab.model.Coordinates;
import ru.itmo.isitmolab.model.Vehicle;
import ru.itmo.isitmolab.ws.VehicleWsService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class VehicleImportTxService {

    @Resource
    private TransactionSynchronizationRegistry txRegistry;

    @Inject
    private MinioStorageService minioStorage;

    @Inject
    private VehicleImportOperationLogger opLogger;

    @Inject
    private VehicleService vehicleService;

    @Inject
    private VehicleDao vehicleDao;

    @Inject
    private VehicleWsService wsHub;

    @Transactional
    public void importVehiclesTx(Long opId,
                                 List<VehicleImportItemDto> items,
                                 byte[] fileBytes,
                                 String finalKey,
                                 String safeName,
                                 String contentType,
                                 long size) {

        final AtomicInteger importedCount = new AtomicInteger(0);

        txRegistry.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
                // 2 minio
                minioStorage.putObject(
                        finalKey,
                        fileBytes,
                        contentType,
                        Map.of("original-name", safeName)
                );
            }

            @Override
            public void afterCompletion(int status) {
                try {
                    if (status == Status.STATUS_COMMITTED) {
                        // success
                        opLogger.markSuccess(opId, importedCount.get(), finalKey, safeName, contentType, size);
                        wsHub.broadcastText("refresh");
                        return;
                    }

                    // rollback
                    minioStorage.removeObjectQuietly(finalKey);
                    opLogger.markFailure(opId, importedCount.get(), null, safeName, contentType, size);

                } catch (Throwable ignored) {
                }
            }
        });

        // 1 database
        for (VehicleImportItemDto item : items) {
            VehicleDto dto = VehicleImportItemDto.toEntity(item);

            vehicleService.checkUniqueVehicleName(dto.getName(), null);

            Coordinates coords = vehicleService.resolveCoordinatesForDto(dto);
            Vehicle v = VehicleDto.toEntity(dto, null);
            v.setCoordinates(coords);

            vehicleDao.save(v);
            importedCount.incrementAndGet();
        }
    }

}