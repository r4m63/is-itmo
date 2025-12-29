package ru.itmo.isitmolab.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ru.itmo.isitmolab.model.VehicleImportOperation;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class VehicleImportOperationDao {

    @PersistenceContext
    private EntityManager em;

    public void save(VehicleImportOperation op) {
        em.persist(op);
    }

    public Optional<VehicleImportOperation> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(em.find(VehicleImportOperation.class, id));
    }

    // public List<VehicleImportOperation> findLastForAdmin(Long adminId, int limit) {
    //     return em.createQuery(
    //                     "SELECT o " +
    //                             "FROM VehicleImportOperation o " +
    //                             "JOIN FETCH o.admin " +
    //                             "WHERE o.admin.id = :adminId " +
    //                             "ORDER BY o.creationTime DESC",
    //                     VehicleImportOperation.class
    //             )
    //             .setParameter("adminId", adminId)
    //             .setMaxResults(limit)
    //             .getResultList();
    // }
    public List<VehicleImportOperation> findLastForAdmin(int limit) {
        return em.createQuery(
                        "SELECT o " +
                                "FROM VehicleImportOperation o " +
                                "ORDER BY o.creationTime DESC",
                        VehicleImportOperation.class
                )
                .setMaxResults(limit)
                .getResultList();
    }

}
