package ru.itmo.isitmolab.util.persontable;

import jakarta.persistence.criteria.*;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static ru.itmo.isitmolab.util.gridtable.DateParsers.parseToLocalDate;

/**
 * Построитель предикатов Criteria API для грид-таблицы владельцев (Person).
 *
 * <h3>Поддерживаемые типы фильтров</h3>
 * <ul>
 *   <li><b>text</b> — contains/equals/startsWith/endsWith/notEqual (регистронезависимо);</li>
 *   <li><b>number</b> — equals/notEqual/lessThan/lessThanOrEqual/greaterThan/greaterThanOrEqual/inRange (Integer/Long);</li>
 *   <li><b>date</b> — equals/lessThan/greaterThan/inRange для полей {@link LocalDateTime} (границы суток);</li>
 *   <li><b>set</b> — IN (...), с приведением значений к типу столбца (enum/int/long/string).</li>
 * </ul>
 *
 * <h3>Особенности</h3>
 * <ul>
 *   <li>Вложенные поля задаются точечной нотацией (например, {@code "admin.login"}).</li>
 *   <li>Для корневой ассоциации {@code admin} выполняется LEFT JOIN; повторные join’ы переиспользуются.</li>
 *   <li>Генерация идёт через Criteria API — безопасно с точки зрения SQL-инъекций.</li>
 * </ul>
 */
@UtilityClass
public class PersonGridPredicateBuilder {

    /**
     * Разрешает путь к полю по строковому идентификатору колонки.
     *
     * <p>Поддержка точечной нотации и LEFT JOIN для {@code admin}.</p>
     *
     * @param root  корневой {@link Root} (обычно {@code Root<Person>})
     * @param colId имя колонки, например {@code "fullName"} или {@code "admin.login"}
     * @return Path к целевому полю; по умолчанию — {@code root.get("id")}
     */
    public static Path<?> resolvePath(Root<?> root, String colId) {
        if (colId == null || colId.isBlank()) return root.get("id");
        if (!colId.contains(".")) return root.get(colId);

        String[] parts = colId.split("\\.");
        Path<?> p = root;
        From<?, ?> from = root;

        for (String part : parts) {
            if ("admin".equals(part)) {
                // Переиспользуем уже созданный join admin, если он есть
                Join<?, ?> existing = null;
                for (Join<?, ?> j : from.getJoins()) {
                    if (j.getAttribute().getName().equals("admin")) {
                        existing = j;
                        break;
                    }
                }
                from = (existing == null) ? from.join("admin", JoinType.LEFT) : existing;
                p = from;
                continue;
            }
            // Обычный сегмент пути
            p = p.get(part);
            if (p instanceof From<?, ?> f) from = f;
        }
        return p;
    }

    /**
     * Строит список предикатов по модели фильтров, совместимой с ag-Grid/гриду.
     *
     * <pre>
     * filterModel = {
     *   "fullName": {"filterType":"text","type":"contains","filter":"ivan"},
     *   "age": {"filterType":"number","type":"inRange","filter":18,"filterTo":60},
     *   "createdAt": {"filterType":"date","type":"equals","dateFrom":"2025-10-10"},
     *   "role": {"filterType":"set","values":["ADMIN","USER"]}
     * }
     * </pre>
     *
     * @param cb          {@link CriteriaBuilder}
     * @param root        {@link Root} сущности
     * @param filterModel карта «колонка → параметры фильтра»; может быть пустой
     * @return список предикатов (возможно пустой)
     */
    public static List<Predicate> build(CriteriaBuilder cb, Root<?> root, Map<String, Object> filterModel) {
        List<Predicate> out = new ArrayList<>();
        if (filterModel == null || filterModel.isEmpty()) return out;

        for (var entry : filterModel.entrySet()) {
            String col = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> fm = (Map<String, Object>) entry.getValue();
            String filterType = (String) fm.get("filterType");
            Path<?> path = resolvePath(root, col);

            switch (String.valueOf(filterType)) {
                case "text"   -> handleText(cb, out, path, fm);
                case "number" -> handleNumber(cb, out, path, fm);
                case "date"   -> handleDate(cb, out, path, fm);
                case "set"    -> handleSet(cb, out, path, fm);
                default       -> { /* неизвестный тип — игнорируем */ }
            }
        }
        return out;
    }

    /** Обработка текстовых фильтров (регистронезависимо). */
    private static void handleText(CriteriaBuilder cb, List<Predicate> out, Path<?> path, Map<String, Object> fm) {
        String type = (String) fm.get("type");
        String val = (String) fm.get("filter");
        if (val == null || val.isBlank()) return;

        Expression<String> exp = cb.lower(path.as(String.class));
        String p = val.toLowerCase(Locale.ROOT);

        switch (type) {
            case "contains"   -> out.add(cb.like(exp, "%" + p + "%"));
            case "equals"     -> out.add(cb.equal(exp, p));
            case "startsWith" -> out.add(cb.like(exp, p + "%"));
            case "endsWith"   -> out.add(cb.like(exp, "%" + p));
            case "notEqual"   -> out.add(cb.notEqual(exp, p));
            default           -> { /* неизвестная операция — игнор */ }
        }
    }

    /** Обработка числовых фильтров (поддержаны Integer/Long). */
    private static void handleNumber(CriteriaBuilder cb, List<Predicate> out, Path<?> path, Map<String, Object> fm) {
        String type = (String) fm.get("type");
        Number f1 = toNumber(fm.get("filter"));
        Number f2 = toNumber(fm.get("filterTo"));

        Class<?> jt = path.getJavaType();
        if (jt == Integer.class || jt == Integer.TYPE) {
            addNumber(cb, out, type, path.as(Integer.class),
                    f1 != null ? f1.intValue() : null,
                    f2 != null ? f2.intValue() : null);
        } else if (jt == Long.class || jt == Long.TYPE) {
            addNumber(cb, out, type, path.as(Long.class),
                    f1 != null ? f1.longValue() : null,
                    f2 != null ? f2.longValue() : null);
        }
        // При необходимости добавить Float/Double/BigDecimal аналогично GridTablePredicateBuilder
    }

    /**
     * Универсальный аппликатор числовых операций.
     * Поддерживает полузамкнутые интервалы для {@code inRange}.
     */
    private static <T extends Number & Comparable<T>> void addNumber(
            CriteriaBuilder cb, List<Predicate> out, String type,
            Expression<T> num, T v1, T v2
    ) {
        if (v1 == null && !"inRange".equals(type)) return;

        switch (type) {
            case "equals"             -> out.add(cb.equal(num, v1));
            case "notEqual"           -> out.add(cb.notEqual(num, v1));
            case "lessThan"           -> out.add(cb.lessThan(num, v1));
            case "lessThanOrEqual"    -> out.add(cb.lessThanOrEqualTo(num, v1));
            case "greaterThan"        -> out.add(cb.greaterThan(num, v1));
            case "greaterThanOrEqual" -> out.add(cb.greaterThanOrEqualTo(num, v1));
            case "inRange" -> {
                if (v1 != null && v2 != null)
                    out.add(cb.and(cb.greaterThanOrEqualTo(num, v1),
                            cb.lessThanOrEqualTo(num, v2)));
                else if (v1 != null) out.add(cb.greaterThanOrEqualTo(num, v1));
                else if (v2 != null) out.add(cb.lessThanOrEqualTo(num, v2));
            }
            default -> { /* неизвестная операция — игнор */ }
        }
    }

    /** Обработка датовых фильтров для полей типа {@link LocalDateTime}. */
    private static void handleDate(CriteriaBuilder cb, List<Predicate> out, Path<?> path, Map<String, Object> fm) {
        if (!LocalDateTime.class.isAssignableFrom(path.getJavaType())) return;

        String type = (String) fm.get("type");
        String d1s = (String) fm.get("dateFrom");
        String d2s = (String) fm.get("dateTo");
        if (d1s == null || d1s.isBlank()) return;

        LocalDate d1 = parseToLocalDate(d1s);
        if (d1 == null) return;

        LocalDateTime start = d1.atStartOfDay();
        Expression<LocalDateTime> dt = path.as(LocalDateTime.class);

        switch (type) {
            case "equals" -> {
                LocalDateTime end = d1.plusDays(1).atStartOfDay();
                out.add(cb.between(dt, start, end));            // [start; nextDay)
            }
            case "lessThan" -> out.add(cb.lessThan(dt, start)); // < startOfDay
            case "greaterThan" -> {
                LocalDateTime end = d1.plusDays(1).atStartOfDay();
                out.add(cb.greaterThanOrEqualTo(dt, end));      // >= startOfNextDay
            }
            case "inRange" -> {
                LocalDate d2 = parseToLocalDate(d2s);
                if (d2 == null) d2 = d1;
                LocalDateTime end = d2.plusDays(1).atStartOfDay();
                out.add(cb.between(dt, start, end));            // [start; endOfRightDay)
            }
            default -> { /* неизвестная операция — игнор */ }
        }
    }

    /** Обработка множества значений (IN) c приведением типов. */
    private static void handleSet(CriteriaBuilder cb, List<Predicate> out, Path<?> path, Map<String, Object> fm) {
        @SuppressWarnings("unchecked")
        List<String> values = (List<String>) fm.get("values");
        if (values == null || values.isEmpty()) return;

        CriteriaBuilder.In<Object> in = cb.in(path);
        for (String v : values) in.value(castForPath(path, v));
        out.add(in);
    }

    /**
     * Приведение строкового значения к Java-типу столбца.
     * Поддержаны enum, Integer, Long; по умолчанию — String.
     */
    @SuppressWarnings("unchecked")
    private static Object castForPath(Path<?> path, String value) {
        Class<?> t = path.getJavaType();
        if (t.isEnum()) return Enum.valueOf((Class<Enum>) t, value);
        if (t.equals(Integer.class) || t.equals(Integer.TYPE)) return Integer.valueOf(value);
        if (t.equals(Long.class)    || t.equals(Long.TYPE))    return Long.valueOf(value);
        return value;
    }

    /** Универсальный парсер числа из Object (Number или строка через BigDecimal). */
    private static Number toNumber(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n;
        return new BigDecimal(o.toString());
    }
}
