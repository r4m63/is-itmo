package ru.itmo.isitmolab.dao;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import ru.itmo.isitmolab.dto.GridTableRequest;
import ru.itmo.isitmolab.model.Coordinates;
import ru.itmo.isitmolab.model.Vehicle;
import ru.itmo.isitmolab.util.GridTablePredicateBuilder;

import java.util.*;

@ApplicationScoped
public class VehicleDao {

    @PersistenceContext(unitName = "studsPU")
    EntityManager em;

    public void save(Vehicle v) {
        if (v.getId() == null) {
            em.persist(v);
        } else {
            em.merge(v);
        }
    }

    public Optional<Vehicle> findById(Long id) {
        if (id == null)
            return Optional.empty();
        return Optional.ofNullable(em.find(Vehicle.class, id));
    }

    public boolean existsById(Long id) {
        if (id == null) return false;
        Long res = em.createQuery("select count(v) from Vehicle v where v.id = :id", Long.class)
                .setParameter("id", id)
                .getSingleResult();
        return res != null && res > 0;
    }

    public void deleteById(Long id) {
        if (id == null)
            return;
        Vehicle res = em.find(Vehicle.class, id);
        if (res != null)
            em.remove(res);
    }

    public List<Vehicle> findPageByGrid(GridTableRequest req) {
        final int pageSize = Math.max(1, req.endRow - req.startRow); // сколько записей на странице
        final int offset = Math.max(0, req.startRow); // с какого индекса начать

        CriteriaBuilder cb = em.getCriteriaBuilder();

        // запрос только по ID (лёгкий запрос для сортировки/фильтров + пагинации)
        CriteriaQuery<Long> cquery = cb.createQuery(Long.class);
        Root<Vehicle> idRoot = cquery.from(Vehicle.class);

        // Фильтры из filterModel (WHERE)
        List<Predicate> predicates = GridTablePredicateBuilder.build(cb, idRoot, req.filterModel);
        if (!predicates.isEmpty())
            cquery.where(predicates.toArray(new Predicate[0]));

        // Сортировка sortModel
        if (req.sortModel != null && !req.sortModel.isEmpty()) {
            List<Order> orders = new ArrayList<>();
            req.sortModel.forEach(s -> {
                Path<?> p = GridTablePredicateBuilder.resolvePath(idRoot, s.getColId());
                orders.add("desc".equalsIgnoreCase(s.getSort()) ? cb.desc(p) : cb.asc(p));
            });
            cquery.orderBy(orders);
        } else {
            cquery.orderBy(cb.desc(idRoot.get("creationTime")), cb.desc(idRoot.get("id")));
        }

        cquery.select(idRoot.get("id")); // Возвращается только столбец id

        // Пагинация на стороне БД (OFFSET/LIMIT), получаем ид
        List<Long> ids = em.createQuery(cquery)
                .setFirstResult(offset)
                .setMaxResults(pageSize)
                .getResultList();

        if (ids.isEmpty())
            return List.of();

        // грузим полноценные сущности по найденным id с графом
        EntityGraph<Vehicle> graph = getWithCoordsGraph();

        // подтягивает объекты Vehicle с нужными связями (через EntityGraph: coordinates)
        List<Vehicle> items = em.createQuery(
                        "select v from Vehicle v where v.id in :ids", Vehicle.class)
                .setParameter("ids", ids)
                .setHint("jakarta.persistence.loadgraph", graph)
                .getResultList();

        // Сохраняем исходный порядок (IN не гарантирует порядок)
        Map<Long, Integer> rank = new HashMap<>(ids.size() * 2);
        for (int i = 0; i < ids.size(); i++)
            rank.put(ids.get(i), i);
        items.sort(Comparator.comparingInt(v -> rank.getOrDefault(v.getId(), Integer.MAX_VALUE)));

        return items;
    }

    public long countByGrid(GridTableRequest req) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> cnt = cb.createQuery(Long.class);
        Root<Vehicle> root = cnt.from(Vehicle.class);

        List<Predicate> preds = GridTablePredicateBuilder.build(cb, root, req.filterModel);
        cnt.select(cb.count(root));
        if (!preds.isEmpty()) cnt.where(preds.toArray(new Predicate[0]));

        return em.createQuery(cnt).getSingleResult();
    }

    @SuppressWarnings("unchecked")
    private EntityGraph<Vehicle> getWithCoordsGraph() {
        try {
            return (EntityGraph<Vehicle>) em.getEntityGraph("Vehicle.withCoordinates");
        } catch (IllegalArgumentException ex) {
            EntityGraph<Vehicle> g = em.createEntityGraph(Vehicle.class);
            g.addAttributeNodes("coordinates");
            return g;
        }
    }

    public long countByCoordinatesId(Long coordinatesId) {
        return em.createQuery(
                        "select count(v) from Vehicle v where v.coordinates.id = :cid", Long.class)
                .setParameter("cid", coordinatesId)
                .getSingleResult();
    }

    public void reassignCoordinates(Long fromCoordinatesId, Long toCoordinatesId) {
        if (fromCoordinatesId == null || toCoordinatesId == null) return;
        Coordinates toRef = em.getReference(Coordinates.class, toCoordinatesId);
        em.createQuery(
                        "update Vehicle v set v.coordinates = :to where v.coordinates.id = :from")
                .setParameter("to", toRef)
                .setParameter("from", fromCoordinatesId)
                .executeUpdate();
    }

    public boolean existsByName(String name) {
        if (name == null) return false;
        Long cnt = em.createQuery(
                        "select count(v) from Vehicle v where v.name = :name", Long.class)
                .setParameter("name", name)
                .getSingleResult();
        return cnt != null && cnt > 0;
    }

    public boolean existsByNameAndIdNot(String name, Long id) {
        if (name == null || id == null) return false;
        Long cnt = em.createQuery(
                        "select count(v) from Vehicle v where v.name = :name and v.id <> :id", Long.class)
                .setParameter("name", name)
                .setParameter("id", id)
                .getSingleResult();
        return cnt != null && cnt > 0;
    }

    public Vehicle findByNameWithPessimisticLock(String name) {
        try {
            return em.createQuery(
                            "select v from Vehicle v where v.name = :name", Vehicle.class)
                    .setParameter("name", name)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE) // Блокируем запись
                    .setMaxResults(1)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public Vehicle findByNameAndIdNotWithPessimisticLock(String name, Long excludeId) {
        try {
            return em.createQuery(
                            "select v from Vehicle v where v.name = :name and v.id != :excludeId", Vehicle.class)
                    .setParameter("name", name)
                    .setParameter("excludeId", excludeId)
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE) // Блокируем запись
                    .setMaxResults(1)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public Vehicle findByNameWithOptimisticLock(String name) {
        try {
            return em.createQuery(
                            "select v from Vehicle v where v.name = :name", Vehicle.class)
                    .setParameter("name", name)
                    .setLockMode(LockModeType.OPTIMISTIC) // Используем Optimistic Lock
                    .setMaxResults(1)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public Vehicle findByNameAndIdNotWithOptimisticLock(String name, Long excludeId) {
        try {
            return em.createQuery(
                            "select v from Vehicle v where v.name = :name and v.id != :excludeId", Vehicle.class)
                    .setParameter("name", name)
                    .setParameter("excludeId", excludeId)
                    .setLockMode(LockModeType.OPTIMISTIC) // Используем Optimistic Lock
                    .setMaxResults(1)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    public Optional<Vehicle> findByName(String name) {
        if (name == null) return Optional.empty();
        try {
            Vehicle vehicle = em.createQuery(
                            "select v from Vehicle v where v.name = :name", Vehicle.class)
                    .setParameter("name", name)
                    .setMaxResults(1)
                    .getSingleResult();
            return Optional.of(vehicle);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public Optional<Vehicle> findByNameAndIdNot(String name, Long excludeId) {
        // excludeId - при update проверить уникальность имени, исключая эту запись (есть ли другое ТС с таким именем)
        if (name == null || excludeId == null) return Optional.empty();
        try {
            Vehicle vehicle = em.createQuery(
                            "select v from Vehicle v where v.name = :name and v.id != :excludeId", Vehicle.class)
                    .setParameter("name", name)
                    .setParameter("excludeId", excludeId)
                    .setMaxResults(1)
                    .getSingleResult();
            return Optional.of(vehicle);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

}
