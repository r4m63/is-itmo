package ru.itmo.isitmolab.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import ru.itmo.isitmolab.model.Coordinates;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoordinatesDto {
    private Long id;
    @NotNull(message = "Заполните X.")
    private Double x;
    @NotNull(message = "Заполните Y.")
    private Float y;
    private Integer vehiclesCount;

    public static CoordinatesDto toDto(Coordinates c, Integer vehiclesCount) {
        if (c == null) return null;
        return CoordinatesDto.builder()
                .id(c.getId())
                .x(c.getX())
                .y(c.getY())
                .vehiclesCount(vehiclesCount)
                .build();
    }

    public static CoordinatesDto toShort(Coordinates c) {
        if (c == null) return null;
        return CoordinatesDto.builder()
                .id(c.getId())
                .x(c.getX())
                .y(c.getY())
                .build();
    }

}
