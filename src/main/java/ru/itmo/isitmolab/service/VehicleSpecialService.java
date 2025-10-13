package ru.itmo.isitmolab.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import ru.itmo.isitmolab.dao.VehicleSpecialDao;
import ru.itmo.isitmolab.dto.VehicleDto;
import ru.itmo.isitmolab.model.Vehicle;

import java.util.*;

@ApplicationScoped
public class VehicleSpecialService {

    @Inject
    VehicleSpecialDao specialDao;

    @PersistenceContext(unitName = "studsPU")
    EntityManager em;

    @SuppressWarnings("unchecked")
    private EntityGraph<Vehicle> graph() {
        try {
            return (EntityGraph<Vehicle>) em.getEntityGraph("Vehicle.withCoordinatesAdmin");
        } catch (IllegalArgumentException ex) {
            EntityGraph<Vehicle> g = em.createEntityGraph(Vehicle.class);
            g.addAttributeNodes("coordinates", "admin");
            return g;
        }
    }

    private Optional<Vehicle> loadOne(Long id) {
        if (id == null) return Optional.empty();
        Map<String, Object> hints = Map.of("jakarta.persistence.loadgraph", graph());
        return Optional.ofNullable(em.find(Vehicle.class, id, hints));
    }

    private List<Vehicle> loadManyPreserveOrder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Vehicle> items = em.createQuery(
                        "select v from Vehicle v where v.id in :ids", Vehicle.class)
                .setParameter("ids", ids)
                .setHint("jakarta.persistence.loadgraph", graph())
                .getResultList();

        Map<Long, Vehicle> byId = new HashMap<>(items.size() * 2);
        for (Vehicle v : items) byId.put(v.getId(), v);

        List<Vehicle> ordered = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Vehicle v = byId.get(id);
            if (v != null) ordered.add(v);
        }
        return ordered;
    }

    @Transactional
    public Optional<VehicleDto> findAnyWithMinDistance() {
        return specialDao.findAnyWithMinDistanceId()
                .flatMap(this::loadOne)
                .map(VehicleDto::toDto);
    }

    public long countFuelConsumptionGreaterThan(float v) {
        return specialDao.countFuelConsumptionGreaterThan(v);
    }

    @Transactional
    public List<VehicleDto> listFuelConsumptionGreaterThan(float v) {
        List<Long> ids = specialDao.listFuelConsumptionGreaterThanIds(v);
        return loadManyPreserveOrder(ids).stream().map(VehicleDto::toDto).toList();
    }

    @Transactional
    public List<VehicleDto> listByType(String type) {
        List<Long> ids = specialDao.listByTypeIds(type);
        return loadManyPreserveOrder(ids).stream().map(VehicleDto::toDto).toList();
    }

    @Transactional
    public List<VehicleDto> listByEnginePowerBetween(Integer min, Integer max) {
        List<Long> ids = specialDao.listByEnginePowerBetweenIds(min, max);
        return loadManyPreserveOrder(ids).stream().map(VehicleDto::toDto).toList();
    }
}
