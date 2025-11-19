package ru.itmo.isitmolab.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.itmo.isitmolab.model.FuelType;
import ru.itmo.isitmolab.model.VehicleType;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleImportItemDto {

    @NotBlank(message = "name обязателен и не может быть пустым")
    private String name;
    @NotBlank(message = "type обязателен")
    @Pattern(regexp = "CAR|HELICOPTER|MOTORCYCLE|CHOPPER", message = "type должен быть одним из: CAR, HELICOPTER, MOTORCYCLE, CHOPPER")
    private String type;
    @Positive(message = "enginePower должен быть > 0")
    private Integer enginePower;
    @NotNull(message = "numberOfWheels обязателен")
    @Positive(message = "numberOfWheels должен быть > 0")
    private Integer numberOfWheels;
    @Positive(message = "capacity должен быть > 0")
    private Integer capacity;
    @Positive(message = "distanceTravelled должен быть > 0")
    private Integer distanceTravelled;
    @NotNull(message = "fuelConsumption обязателен")
    @Positive(message = "fuelConsumption должен быть > 0")
    private Float fuelConsumption;
    @NotBlank(message = "fuelType обязателен")
    @Pattern(regexp = "KEROSENE|MANPOWER|NUCLEAR", message = "fuelType должен быть одним из: KEROSENE, MANPOWER, NUCLEAR")
    private String fuelType;
    @NotNull(message = "coordinates обязательны")
    @Valid
    private CoordinatesNestedDto coordinates;


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CoordinatesNestedDto {
        @NotNull(message = "coordinates.x обязателен")
        @DecimalMax(value = "613", message = "Координата X должна быть <= 613")
        private Double x;

        @NotNull(message = "coordinates.y обязателен")
        @DecimalMax(value = "962", message = "Координата Y должна быть <= 962")
        private Float y;
    }

    public static VehicleDto toEntity(VehicleImportItemDto item) {
        if (item == null) return null;
        VehicleDto dto = new VehicleDto();
        dto.setName(item.getName());
        dto.setType(VehicleType.valueOf(item.getType()));
        dto.setEnginePower(item.getEnginePower());
        dto.setNumberOfWheels(item.getNumberOfWheels());
        dto.setCapacity(item.getCapacity());
        dto.setDistanceTravelled(item.getDistanceTravelled());
        dto.setFuelConsumption(item.getFuelConsumption());
        dto.setFuelType(FuelType.valueOf(item.getFuelType()));
        if (item.getCoordinates() != null) {
            dto.setCoordinatesX(item.getCoordinates().getX());
            dto.setCoordinatesY(item.getCoordinates().getY());
        }
        return dto;
    }

    public static VehicleImportItemDto fromEntity(VehicleDto dto) {
        if (dto == null) return null;
        VehicleImportItemDto item = new VehicleImportItemDto();
        item.setName(dto.getName());
        item.setType(String.valueOf(dto.getType()));
        item.setEnginePower(dto.getEnginePower());
        item.setNumberOfWheels(dto.getNumberOfWheels());
        item.setCapacity(dto.getCapacity());
        item.setDistanceTravelled(dto.getDistanceTravelled());
        item.setFuelConsumption(dto.getFuelConsumption());
        item.setFuelType(String.valueOf(dto.getFuelType()));
        if (dto.getCoordinatesX() != null || dto.getCoordinatesY() != null) {
            CoordinatesNestedDto coords = new CoordinatesNestedDto();
            coords.setX(dto.getCoordinatesX());
            coords.setY(dto.getCoordinatesY());
            item.setCoordinates(coords);
        }
        return item;
    }
}

