package ru.itmo.isitmolab.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.itmo.isitmolab.model.VehicleImportOperation;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleImportHistoryItemDto {
    private Long id;
    private boolean success;
    private Integer importedCount;
    private LocalDateTime creationTime;
    private String fileName;
    private Long fileSize;
    private String fileDownloadUrl;

    public static VehicleImportHistoryItemDto toDto(VehicleImportOperation op) {

        boolean success = Boolean.TRUE.equals(op.getStatus());

        String fileKey = op.getFileObjectKey();
        String downloadUrl = (fileKey == null || fileKey.isBlank())
                ? null
                : "/api/vehicle/import/history/" + op.getId() + "/file";

        return VehicleImportHistoryItemDto.builder()
                .id(op.getId())
                .success(success)
                .importedCount(success ? op.getImportedCount() : null)
                .creationTime(op.getCreationTime())
                .fileName(op.getFileName())
                .fileSize(op.getFileSize())
                .fileDownloadUrl(downloadUrl)
                .build();
    }
}
