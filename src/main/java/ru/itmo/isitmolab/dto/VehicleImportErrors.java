package ru.itmo.isitmolab.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleImportErrors {

    private List<RowError> errors;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RowError {
        private Integer rowNumber;
        private String field;
        private String message;
    }
}