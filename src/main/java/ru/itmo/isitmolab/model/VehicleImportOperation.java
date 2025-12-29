package ru.itmo.isitmolab.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "vehicle_import_operation")
public class VehicleImportOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "status")
    private Boolean status;

    @Column(name = "imported_count")
    private Integer importedCount;

    @Column(name = "file_object_key")
    private String fileObjectKey;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_content_type")
    private String fileContentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "creation_time", nullable = false, updatable = false)
    private LocalDateTime creationTime;

    @PrePersist
    public void prePersist() {
        if (creationTime == null) {
            creationTime = LocalDateTime.now();
        }
    }
}
