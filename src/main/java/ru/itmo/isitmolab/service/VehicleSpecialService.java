package ru.itmo.isitmolab.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import ru.itmo.isitmolab.dao.VehicleSpecialDao;
import ru.itmo.isitmolab.dto.VehicleDto;
import ru.itmo.isitmolab.model.Vehicle;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class VehicleSpecialService {

    @Inject
    VehicleSpecialDao specialDao;

    public Optional<VehicleDto> findAnyWithMinDistance() {
        return specialDao.findAnyWithMinDistanceId()
                .flatMap(specialDao::loadOneWithGraph)
                .map(VehicleDto::toDto);
    }

    public long countFuelConsumptionGreaterThan(float v) {
        return specialDao.countFuelConsumptionGreaterThan(v);
    }

    public List<VehicleDto> listFuelConsumptionGreaterThan(float v) {
        List<Long> ids = specialDao.listFuelConsumptionGreaterThanIds(v);
        List<Vehicle> items = specialDao.loadManyPreserveOrder(ids);
        return items.stream().map(VehicleDto::toDto).toList();
    }

    public List<VehicleDto> listByType(String type) {
        List<Long> ids = specialDao.listByTypeIds(type);
        List<Vehicle> items = specialDao.loadManyPreserveOrder(ids);
        return items.stream().map(VehicleDto::toDto).toList();
    }

    public List<VehicleDto> listByEnginePowerBetween(Integer min, Integer max) {
        List<Long> ids = specialDao.listByEnginePowerBetweenIds(min, max);
        List<Vehicle> items = specialDao.loadManyPreserveOrder(ids);
        return items.stream().map(VehicleDto::toDto).toList();
    }

}
