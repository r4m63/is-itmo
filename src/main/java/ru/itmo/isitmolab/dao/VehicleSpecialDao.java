package ru.itmo.isitmolab.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class VehicleSpecialDao {

    @PersistenceContext(unitName = "studsPU")
    EntityManager em;

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
        return res.stream().map(Number::longValue).toList();
    }

    public List<Long> listByTypeIds(String type) {
        @SuppressWarnings("unchecked")
        List<Number> res = em.createNativeQuery(
                        "select id from fn_vehicle_list_by_type(?1)")
                .setParameter(1, type)
                .getResultList();
        return res.stream().map(Number::longValue).toList();
    }

    public List<Long> listByEnginePowerBetweenIds(Integer min, Integer max) {
        @SuppressWarnings("unchecked")
        List<Number> res = em.createNativeQuery(
                        "select id from fn_vehicle_list_engine_between(?1, ?2)")
                .setParameter(1, min)
                .setParameter(2, max)
                .getResultList();
        return res.stream().map(Number::longValue).toList();
    }
}
