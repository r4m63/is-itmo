package ru.itmo.isitmolab.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import jakarta.transaction.Transactional;
import ru.itmo.isitmolab.dto.GridTableRequest;
import ru.itmo.isitmolab.model.Coordinates;
import ru.itmo.isitmolab.util.gridtable.GridTablePredicateBuilder;

import java.util.*;

@ApplicationScoped
public class CoordinatesDao {

    @PersistenceContext(unitName = "studsPU")
    EntityManager em;

    @Transactional
    public Coordinates save(Coordinates c) {
        if (c.getId() == null) {
            em.persist(c);
            return c;
        } else {
            return em.merge(c);
        }
    }

    public Optional<Coordinates> findById(Long id) {
        return Optional.ofNullable(em.find(Coordinates.class, id));
    }

    @Transactional
    public void deleteById(Long id) {
        if (id == null) return;
        Coordinates ref = em.find(Coordinates.class, id);
        if (ref != null) em.remove(ref);
    }

    public boolean existsById(Long id) {
        if (id == null) return false;
        Long cnt = em.createQuery(
                        "select count(c) from Coordinates c where c.id = :id", Long.class)
                .setParameter("id", id)
                .getSingleResult();
        return cnt != null && cnt > 0;
    }

    public List<Coordinates> findPageByGrid(GridTableRequest req) {
        final int pageSize = Math.max(1, req.endRow - req.startRow);
        final int first = Math.max(0, req.startRow);

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Coordinates> cq = cb.createQuery(Coordinates.class);
        Root<Coordinates> root = cq.from(Coordinates.class);

        List<Predicate> predicates = GridTablePredicateBuilder.build(cb, root, req.filterModel);
        if (!predicates.isEmpty()) cq.where(predicates.toArray(new Predicate[0]));

        if (req.sortModel != null && !req.sortModel.isEmpty()) {
            List<Order> orders = new ArrayList<>();
            req.sortModel.forEach(s -> {
                Path<?> p = GridTablePredicateBuilder.resolvePath(root, s.getColId());
                orders.add("desc".equalsIgnoreCase(s.getSort()) ? cb.desc(p) : cb.asc(p));
            });
            cq.orderBy(orders);
        } else {
            cq.orderBy(cb.asc(root.get("id")));
        }

        cq.select(root);

        return em.createQuery(cq)
                .setFirstResult(first)
                .setMaxResults(pageSize)
                .getResultList();
    }

    public long countByGrid(GridTableRequest req) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cnt = cb.createQuery(Long.class);
        Root<Coordinates> root = cnt.from(Coordinates.class);

        List<Predicate> preds = GridTablePredicateBuilder.build(cb, root, req.filterModel);
        cnt.select(cb.count(root));
        if (!preds.isEmpty()) cnt.where(preds.toArray(new Predicate[0]));

        return em.createQuery(cnt).getSingleResult();
    }

    @Transactional
    public Coordinates findOrCreateByXY(Double x, Float y) {
        Coordinates c = em.createQuery(
                        "select c from Coordinates c where c.x = :x and c.y = :y", Coordinates.class)
                .setParameter("x", x)
                .setParameter("y", y)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (c != null) return c;

        c = Coordinates.builder().x(x).y(y).build();
        em.persist(c);
        return c;
    }

    public Map<Long, Integer> countVehiclesForCoordinatesIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        List<Object[]> rows = em.createQuery(
                        "select c.id, count(v.id) " +
                                "from Coordinates c left join Vehicle v on v.coordinates.id = c.id " +
                                "where c.id in :ids " +
                                "group by c.id", Object[].class)
                .setParameter("ids", ids)
                .getResultList();

        Map<Long, Integer> out = new HashMap<>();
        for (Object[] r : rows) out.put((Long) r[0], ((Long) r[1]).intValue());
        ids.forEach(id -> out.putIfAbsent(id, 0));
        return out;
    }

    public int countVehiclesForCoordinatesId(Long id) {
        Long c = em.createQuery(
                        "select count(v.id) from Vehicle v where v.coordinates.id = :id", Long.class)
                .setParameter("id", id)
                .getSingleResult();
        return c.intValue();
    }

    public List<Coordinates> findAllOrdered() {
        return em.createQuery(
                "select c from Coordinates c order by c.id asc",
                Coordinates.class
        ).getResultList();
    }


    public List<Coordinates> search(String q, int limit) {
        int lim = Math.max(1, Math.min(limit, 100));
        if (q == null || q.isBlank()) {
            return em.createQuery("select c from Coordinates c order by c.id asc", Coordinates.class)
                    .setMaxResults(lim)
                    .getResultList();
        }

        String s = q.trim();
        if (s.contains(",")) {
            String[] parts = s.split(",");
            if (parts.length >= 2) {
                try {
                    Double x = Double.valueOf(parts[0].trim());
                    Float y = Float.valueOf(parts[1].trim());
                    return em.createQuery(
                                    "select c from Coordinates c where c.x = :x and c.y = :y order by c.id asc",
                                    Coordinates.class)
                            .setParameter("x", x)
                            .setParameter("y", y)
                            .setMaxResults(lim)
                            .getResultList();
                } catch (NumberFormatException ignored) {
                }
            }
        } else {
            try {
                Double val = Double.valueOf(s);
                return em.createQuery(
                                "select c from Coordinates c " +
                                        "where c.x = :val or c.y = :val " +
                                        "order by c.id asc",
                                Coordinates.class)
                        .setParameter("val", val)
                        .setMaxResults(lim)
                        .getResultList();
            } catch (NumberFormatException ignored) { }
        }

        return em.createQuery("select c from Coordinates c order by c.id asc", Coordinates.class)
                .setMaxResults(lim)
                .getResultList();
    }
}
