package ru.itmo.isitmolab.exception;

import lombok.Getter;

@Getter
public class VehicleNameNotUniqueException extends BusinessException {

    private final String vehicleName;

    public VehicleNameNotUniqueException(String vehicleName) {
        super("VEHICLE_NAME_NOT_UNIQUE", "Транспортное средство с именем '" + vehicleName + "' уже существует");
        this.vehicleName = vehicleName;
    }

}
