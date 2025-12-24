package ru.itmo.isitmolab.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ru.itmo.isitmolab.model.Vehicle;

import java.util.*;

@ApplicationScoped
public class VehicleSpecialDao {

    @PersistenceContext
    EntityManager em;

    @SuppressWarnings("unchecked")
    private EntityGraph<Vehicle> graph() {
        try {
            return (EntityGraph<Vehicle>) em.getEntityGraph("Vehicle.withCoordinates");
        } catch (IllegalArgumentException ex) {
            EntityGraph<Vehicle> g = em.createEntityGraph(Vehicle.class);
            g.addAttributeNodes("coordinates");
            return g;
        }
    }

    // Загрузить одну сущность Vehicle по id с нужным графом (coordinates).
    public Optional<Vehicle> loadOneWithGraph(Long id) {
        if (id == null) return Optional.empty();
        Map<String, Object> hints = Map.of("jakarta.persistence.loadgraph", graph());
        return Optional.ofNullable(em.find(Vehicle.class, id, hints));
    }

    // Загрузить список Vehicle по id с графом и сохранить порядок,
    // переданный во входном списке ids.
    public List<Vehicle> loadManyPreserveOrder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();

        List<Vehicle> items = em.createQuery(
                        "select v from Vehicle v where v.id in :ids", Vehicle.class)
                .setParameter("ids", ids)
                .setHint("jakarta.persistence.loadgraph", graph())
                .getResultList();

        // индексация по id
        Map<Long, Vehicle> byId = new HashMap<>(Math.max(16, items.size() * 2));
        for (Vehicle v : items) byId.put(v.getId(), v);

        // сбор в исходном порядке
        List<Vehicle> ordered = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Vehicle v = byId.get(id);
            if (v != null) ordered.add(v);
        }
        return ordered;
    }

    /* нативные вызовы функций */

    public long countFuelConsumptionGreaterThan(float v) {
        Number n = (Number) em.createNativeQuery(
                        "select fn_vehicle_count_fuel_gt(?1)")
                .setParameter(1, v)
                .getSingleResult();
        return n.longValue();
    }

    public Optional<Long> findAnyWithMinDistanceId() {
        @SuppressWarnings("unchecked")
        List<Number> res = em.createNativeQuery(
                        "select id from fn_vehicle_min_distance()")
                .getResultList();
        return res.isEmpty() ? Optional.empty() : Optional.of(res.get(0).longValue());
    }

    public List<Long> listFuelConsumptionGreaterThanIds(float v) {
        @SuppressWarnings("unchecked")
        List<Number> res = em.createNativeQuery(
                        "select id from fn_vehicle_list_fuel_gt(?1)")
                .setParameter(1, v)
                .getResultList();
        List<Long> out = new ArrayList<>(res.size());
        for (Number n : res) out.add(n.longValue());
        return out;
    }

    public List<Long> listByTypeIds(String type) {
        @SuppressWarnings("unchecked")
        List<Number> res = em.createNativeQuery(
                        "select id from fn_vehicle_list_by_type(?1)")
                .setParameter(1, type)
                .getResultList();
        List<Long> out = new ArrayList<>(res.size());
        for (Number n : res) out.add(n.longValue());
        return out;
    }

    public List<Long> listByEnginePowerBetweenIds(Integer min, Integer max) {
        @SuppressWarnings("unchecked")
        List<Number> res = em.createNativeQuery(
                        "select id from fn_vehicle_list_engine_between(?1, ?2)")
                .setParameter(1, min)
                .setParameter(2, max)
                .getResultList();
        List<Long> out = new ArrayList<>(res.size());
        for (Number n : res) out.add(n.longValue());
        return out;
    }

}
