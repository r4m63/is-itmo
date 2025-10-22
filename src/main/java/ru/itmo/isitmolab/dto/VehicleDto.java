package ru.itmo.isitmolab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.itmo.isitmolab.model.FuelType;
import ru.itmo.isitmolab.model.Vehicle;
import ru.itmo.isitmolab.model.VehicleType;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleDto {

    private Long id;
    @NotBlank
    private String name;
    private LocalDateTime creationTime;
    @NotNull
    private VehicleType type;
    @Positive
    private Integer enginePower;
    @Positive
    private Integer numberOfWheels;
    @Positive
    private Integer capacity;
    @Positive
    private Integer distanceTravelled;
    @Positive
    private Float fuelConsumption;
    @NotNull
    private FuelType fuelType;
    private Long coordinatesId;
    private Double coordinatesX;
    private Float coordinatesY;

    public static VehicleDto toDto(Vehicle v) {
        return VehicleDto.builder()
                .id(v.getId())
                .name(v.getName())
                .creationTime(v.getCreationTime())
                .type(v.getType())
                .enginePower(v.getEnginePower())
                .numberOfWheels(v.getNumberOfWheels())
                .capacity(v.getCapacity())
                .distanceTravelled(v.getDistanceTravelled())
                .fuelConsumption(v.getFuelConsumption())
                .fuelType(v.getFuelType())
                .coordinatesId(v.getCoordinates() != null ? v.getCoordinates().getId() : null)
                .coordinatesX(v.getCoordinates() != null ? v.getCoordinates().getX() : null)
                .coordinatesY(v.getCoordinates() != null ? v.getCoordinates().getY() : null)
                .build();
    }


    public static Vehicle toEntity(VehicleDto dto, Vehicle targetOrNull) {
        Vehicle t = targetOrNull != null ? targetOrNull : new Vehicle();
        t.setName(dto.getName());
        t.setType(dto.getType());
        t.setEnginePower(dto.getEnginePower());
        t.setNumberOfWheels(dto.getNumberOfWheels() != null ? dto.getNumberOfWheels() : 0);
        t.setCapacity(dto.getCapacity());
        t.setDistanceTravelled(dto.getDistanceTravelled());
        t.setFuelConsumption(dto.getFuelConsumption() != null ? dto.getFuelConsumption() : 0f);
        t.setFuelType(dto.getFuelType());
        return t;
    }
}
