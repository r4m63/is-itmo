package ru.itmo.isitmolab.util.gridtable;

import jakarta.persistence.criteria.*;
import lombok.experimental.UtilityClass;
import ru.itmo.isitmolab.model.Vehicle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static ru.itmo.isitmolab.util.gridtable.DateParsers.parseToLocalDate;

/**
 * Вспомогательный класс для построения {@link Predicate} по модели фильтров грид-таблицы.
 *
 * <h3>Поддерживаемые типы фильтров</h3>
 * <ul>
 *   <li><b>text</b> — операции: contains, equals, startsWith, endsWith, notEqual;</li>
 *   <li><b>number</b> — операции: equals, notEqual, lessThan, lessThanOrEqual, greaterThan, greaterThanOrEqual, inRange;</li>
 *   <li><b>date</b> — операции: equals, lessThan, greaterThan, inRange (работают по {@link LocalDateTime} с границами суток);</li>
 *   <li><b>set</b> — список значений (IN), поддерживает enum/числа/строки.</li>
 * </ul>
 *
 * <h3>Особенности и соглашения</h3>
 * <ul>
 *   <li>Колонки могут ссылаться на вложенные пути через точку, например <code>owner.name</code> или <code>admin.login</code>.</li>
 *   <li>Для корневых ассоциаций <code>owner</code> и <code>admin</code> используется LEFT JOIN (создаём/переиспользуем join).</li>
 *   <li>Text-фильтры приводят поле к нижнему регистру (SQL-функция LOWER) и сравнивают со строкой в нижнем регистре.</li>
 *   <li>Date-фильтры предполагают, что поле — {@link LocalDateTime}. Для "equals" берётся интервал [день; следующий день).</li>
 *   <li>Set-фильтры (IN) приводят строковые значения к типу колонки (включая enums).</li>
 *   <li>Безопасность: генерируется Criteria API (параметры биндуются), SQL-инъекции исключены.</li>
 * </ul>
 */
@UtilityClass
public final class GridTablePredicateBuilder {

    /**
     * Разрешает строковый идентификатор колонки (в т.ч. вложенный через точку)
     * в JPA Criteria Path. Поддерживает LEFT JOIN для корневых ассоциаций
     * "owner" и "admin", чтобы можно было писать, например, "owner.name" или
     * "admin.username".
     *
     * Примеры:
     *   resolvePath(root, "name")            -> root.get("name")
     *   resolvePath(root, "owner.name")      -> root.join("owner", LEFT).get("name")
     *   resolvePath(root, "admin.role.code") -> root.join("admin", LEFT).get("role").get("code")
     *
     * @param root  корневой Root<Vehicle>
     * @param colId идентификатор колонки (например, "owner.name"); если пустой — вернёт path до "id"
     * @return Path<?> до нужного поля/ассоциации
     */
    public static Path<?> resolvePath(Root<Vehicle> root, String colId) {
        // Если колонка не задана, по умолчанию считаем, что нужен "id"
        if (colId == null || colId.isBlank()) return root.get("id");

        // Разбиваем путь по точке: "owner.name" -> ["owner","name"]
        String[] parts = colId.split("\\.");
        Path<?> p = root;       // текущий Path, от которого берём .get(...)
        From<?, ?> from = root; // текущая "точка" для join'ов (Root или Join)

        // Идём слева направо по компонентам пути
        for (String part : parts) {
            // Специальный случай: если это корневая ассоциация "owner" или "admin"
            if ("owner".equals(part) || "admin".equals(part)) {
                // Пытаемся переиспользовать уже существующий join, чтобы не плодить дубликаты
                Join<?, ?> existing = null;
                for (Join<?, ?> j : from.getJoins()) {
                    if (j.getAttribute().getName().equals(part)) {
                        existing = j;
                        break;
                    }
                }
                // Если join уже есть — используем его; иначе создаём LEFT JOIN
                from = (existing != null) ? existing : from.join(part, JoinType.LEFT);
                p = from; // текущий Path теперь указывает на join'нутую ассоциацию
                continue; // переходим к следующему компоненту пути
            }

            // Обычный сегмент пути: просто берём под-путь через .get("field")
            p = p.get(part);

            // Если получившийся Path также является From (это возможно для дальнейших join'ов),
            // обновляем "from", чтобы следующие join'ы/получения шли от этой точки.
            if (p instanceof From<?, ?> f) from = f;
        }

        // Возвращаем Path до конечного атрибута/ассоциации
        return p;
    }


    /**
     * Строит список предикатов под переданную модель фильтров.
     *
     * <p>Ожидается структура вида:
     * <pre>
     * filterModel = {
     *   "name": {"filterType":"text","type":"contains","filter":"truck"},
     *   "capacity": {"filterType":"number","type":"inRange","filter":1000,"filterTo":5000},
     *   "creationTime": {"filterType":"date","type":"equals","dateFrom":"2025-10-10"},
     *   "type": {"filterType":"set","values":["TRUCK","CAR"]}
     * }
     * </pre>
     *
     * @param cb          {@link CriteriaBuilder}
     * @param root        {@link Root} сущности
     * @param filterModel карта "колонка" → "описание фильтра"; может быть {@code null}/пустой
     * @return список предикатов (может быть пустым, но не {@code null})
     */
    public static List<Predicate> build(CriteriaBuilder cb, Root<Vehicle> root, Map<String, Object> filterModel) {
        // Коллекция выходных предикатов для передачи в CriteriaQuery.where(...)
        List<Predicate> out = new ArrayList<>();
        // Если фильтров нет — возвращаем пустой список (ничего не ограничиваем)
        if (filterModel == null || filterModel.isEmpty()) return out;

        // Для каждой записи вида "колонка" -> "описание фильтра"
        for (var entry : filterModel.entrySet()) {
            String col = entry.getKey(); // имя колонки, может быть вложенным путём "owner.name"
            @SuppressWarnings("unchecked")
            Map<String, Object> fm = (Map<String, Object>) entry.getValue(); // карта параметров фильтра
            String filterType = (String) fm.get("filterType"); // тип фильтра: text/number/date/set

            // Разрешаем путь колонки в JPA Path (и при необходимости выполняем LEFT JOIN'ы)
            Path<?> path = resolvePath(root, col);

            // В зависимости от типа фильтра вызываем специализированный обработчик.
            // Каждый обработчик добавляет 0..N предикатов в список out.
            switch (String.valueOf(filterType)) {
                case "text" -> handleText(cb, out, path, fm);     // contains/equals/... (регистронезависимо)
                case "number" -> handleNumber(cb, out, path, fm); // =, !=, <, <=, >, >=, inRange
                case "date" -> handleDate(cb, out, path, fm);     // equals/lessThan/greaterThan/inRange (через LocalDateTime)
                case "set" -> handleSet(cb, out, path, fm);       // IN (...)
                default -> { /* неизвестный тип — пропускаем без ошибки */ }
            }
        }

        // Готовый список предикатов возвращаем вызывающему коду;
        // обычно их объединяют через cb.and(...) вне этого метода.
        return out;
    }


    /** Обработка текстовых фильтров: contains/equals/startsWith/endsWith/notEqual (регистронезависимо). */
    private static void handleText(CriteriaBuilder cb, List<Predicate> out, Path<?> path, Map<String, Object> fm) {
        // Тип операции: contains / equals / startsWith / endsWith / notEqual
        String type = (String) fm.get("type");
        // Пользовательское значение для сравнения
        String val = (String) fm.get("filter");
        // Если значение пустое — ничего не добавляем
        if (val == null || val.isBlank()) return;

        // Приводим столбец к нижнему регистру на стороне БД (LOWER(column))
        Expression<String> exp = cb.lower(path.as(String.class));
        // И приводим искомую строку к нижнему регистру на стороне Java
        String p = val.toLowerCase(Locale.ROOT);

        // В зависимости от операции генерируем нужный предикат.
        switch (type) {
            case "contains"   -> out.add(cb.like(exp, "%" + p + "%")); // ... LIKE %value%
            case "equals"     -> out.add(cb.equal(exp, p));            // LOWER(col) = value
            case "startsWith" -> out.add(cb.like(exp, p + "%"));       // ... LIKE value%
            case "endsWith"   -> out.add(cb.like(exp, "%" + p));       // ... LIKE %value
            case "notEqual"   -> out.add(cb.notEqual(exp, p));         // LOWER(col) <> value
            default           -> { /* неизвестная операция — игнорируем */ }
        }
    }

    /** Обработка числовых фильтров, с приведением типов к целевому Java-типу столбца. */
    private static void handleNumber(CriteriaBuilder cb, List<Predicate> out, Path<?> path, Map<String, Object> fm) {
        // Тип операции (=, !=, <, <=, >, >=, inRange)
        String type = (String) fm.get("type");
        // Основное значение (левая граница для inRange)
        Number f1 = toNumber(fm.get("filter"));
        // Дополнительное значение (правая граница для inRange)
        Number f2 = toNumber(fm.get("filterTo"));

        // Определяем фактический Java-тип атрибута в JPA-модели (Integer/Long/Float/Double/BigDecimal)
        Class<?> jt = path.getJavaType();

        // Для каждого поддерживаемого типа приводим входные Number к целевому типу
        // и делегируем создание предиката в универсальный addNumber(...).
        if (jt == Integer.class || jt == Integer.TYPE) {
            addNumber(cb, out, type, path.as(Integer.class),
                    f1 != null ? f1.intValue()   : null,
                    f2 != null ? f2.intValue()   : null);

        } else if (jt == Long.class || jt == Long.TYPE) {
            addNumber(cb, out, type, path.as(Long.class),
                    f1 != null ? f1.longValue()  : null,
                    f2 != null ? f2.longValue()  : null);

        } else if (jt == Float.class || jt == Float.TYPE) {
            addNumber(cb, out, type, path.as(Float.class),
                    f1 != null ? f1.floatValue() : null,
                    f2 != null ? f2.floatValue() : null);

        } else if (jt == Double.class || jt == Double.TYPE) {
            addNumber(cb, out, type, path.as(Double.class),
                    f1 != null ? f1.doubleValue(): null,
                    f2 != null ? f2.doubleValue(): null);

        } else if (jt == BigDecimal.class) {
            // Для BigDecimal делаем точное представление через строку (во избежание ошибок двоичной дроби)
            addNumber(cb, out, type, path.as(BigDecimal.class),
                    f1 != null ? new BigDecimal(f1.toString()) : null,
                    f2 != null ? new BigDecimal(f2.toString()) : null);
        }
    }


    /**
     * Универсальный аппликатор для числовых операций.
     * <p>Для {@code inRange} поддерживает полузамкнутые случаи: [v1; +∞) или (-∞; v2] при отсутствии одной из границ.</p>
     */
    private static <T extends Number & Comparable<T>> void addNumber(
            CriteriaBuilder cb, List<Predicate> out, String type,
            Expression<T> num, T v1, T v2
    ) {
        // Для всех операций кроме inRange необходима левая граница (v1).
        if (v1 == null && !"inRange".equals(type)) return;

        // Генерируем соответствующий числовой предикат.
        switch (type) {
            case "equals"              -> out.add(cb.equal(num, v1));                 // num = v1
            case "notEqual"            -> out.add(cb.notEqual(num, v1));              // num <> v1
            case "lessThan"            -> out.add(cb.lessThan(num, v1));              // num < v1
            case "lessThanOrEqual"     -> out.add(cb.lessThanOrEqualTo(num, v1));     // num <= v1
            case "greaterThan"         -> out.add(cb.greaterThan(num, v1));           // num > v1
            case "greaterThanOrEqual"  -> out.add(cb.greaterThanOrEqualTo(num, v1));  // num >= v1
            case "inRange" -> {
                // Полный диапазон [v1; v2]
                if (v1 != null && v2 != null) {
                    out.add(cb.and(cb.greaterThanOrEqualTo(num, v1),
                            cb.lessThanOrEqualTo(num, v2)));
                    // Нижняя полу-граница [v1; +∞)
                } else if (v1 != null) {
                    out.add(cb.greaterThanOrEqualTo(num, v1));
                    // Верхняя полу-граница (-∞; v2]
                } else if (v2 != null) {
                    out.add(cb.lessThanOrEqualTo(num, v2));
                }
            }
            default -> { /* неизвестная операция — игнор */ }
        }
    }

    /**
     * Обработка дат: ожидается, что поле — {@link LocalDateTime}.
     *
     * <p>Семантика:
     * <ul>
     *   <li><b>equals</b>: интервал [dateFrom; dateFrom+1day) — включает все записи этого календарного дня;</li>
     *   <li><b>lessThan</b>: strictly before начало дня {@code dateFrom};</li>
     *   <li><b>greaterThan</b>: greaterThanOrEqual начало дня следующего дня (исключая сам {@code dateFrom});</li>
     *   <li><b>inRange</b>: интервал [dateFrom; dateTo+1day) (если dateTo пуст — равносилен equals).</li>
     * </ul>
     * Даты парсятся с помощью {@link DateParsers#parseToLocalDate(String)}.</p>
     */
    private static void handleDate(CriteriaBuilder cb, List<Predicate> out, Path<?> path, Map<String, Object> fm) {
        // Если тип свойства не LocalDateTime — фильтр по дате не применим
        if (!LocalDateTime.class.isAssignableFrom(path.getJavaType())) return;

        String type = (String) fm.get("type");
        String d1s = (String) fm.get("dateFrom"); // левая граница/день
        String d2s = (String) fm.get("dateTo");   // правая граница/день (опционально)

        // Без dateFrom фильтр не строим
        if (d1s == null || d1s.isBlank()) return;

        // Парсим dateFrom -> LocalDate
        LocalDate d1 = parseToLocalDate(d1s);
        if (d1 == null) return; // некорректный формат даты — игнор

        // Начало суток dateFrom
        LocalDateTime start = d1.atStartOfDay();
        // Выражение столбца как LocalDateTime
        Expression<LocalDateTime> dt = path.as(LocalDateTime.class);

        switch (type) {
            case "equals" -> {
                // [start; nextDayStart)
                LocalDateTime end = d1.plusDays(1).atStartOfDay();
                out.add(cb.between(dt, start, end));
            }
            case "lessThan" ->
                // строго раньше начала дня dateFrom
                    out.add(cb.lessThan(dt, start));
            case "greaterThan" -> {
                // >= начала следующего дня (то есть строго позже любого момента dateFrom)
                LocalDateTime end = d1.plusDays(1).atStartOfDay();
                out.add(cb.greaterThanOrEqualTo(dt, end));
            }
            case "inRange" -> {
                // Правая граница: если не задана — считаем равной d1 (фактически equals)
                LocalDate d2 = parseToLocalDate(d2s);
                if (d2 == null) d2 = d1;
                // [start; endOfRightDay)
                LocalDateTime end = d2.plusDays(1).atStartOfDay();
                out.add(cb.between(dt, start, end));
            }
            default -> { /* неизвестная операция — игнор */ }
        }
    }

    /**
     * Обработка множества значений (IN).
     *
     * <p>Значения приводятся к типу колонки (enum/числа/строки). Пустой список игнорируется.</p>
     */
    private static void handleSet(CriteriaBuilder cb, List<Predicate> out, Path<?> path, Map<String, Object> fm) {
        @SuppressWarnings("unchecked")
        List<String> values = (List<String>) fm.get("values");
        // Нет значений — нечего фильтровать
        if (values == null || values.isEmpty()) return;

        // Строим IN (...) по типу целевого поля
        CriteriaBuilder.In<Object> in = cb.in(path);
        for (String v : values) {
            // Приводим каждое значение к типу колонки (Enum/числа/строка)
            in.value(castForPath(path, v));
        }
        out.add(in);
    }

    /**
     * Приведение строкового значения к Java-типу колонки.
     *
     * <p>Поддерживаются: enum, Integer/Long/Double/Float/BigDecimal, String (по умолчанию).</p>
     */
    @SuppressWarnings("unchecked")
    private static Object castForPath(Path<?> path, String value) {
        Class<?> t = path.getJavaType();
        // Для enum — используем Enum.valueOf по имени константы
        if (t.isEnum()) return Enum.valueOf((Class<Enum>) t, value);
        // Числовые типы: парсинг к нужному классу-обёртке/примитиву
        if (t.equals(Integer.class) || t.equals(Integer.TYPE)) return Integer.valueOf(value);
        if (t.equals(Long.class)    || t.equals(Long.TYPE))    return Long.valueOf(value);
        if (t.equals(Double.class)  || t.equals(Double.TYPE))  return Double.valueOf(value);
        if (t.equals(Float.class)   || t.equals(Float.TYPE))   return Float.valueOf(value);
        if (t.equals(BigDecimal.class))                        return new BigDecimal(value);
        // По умолчанию — строка как есть
        return value;
    }

    /**
     * Универсальный парсер чисел из Object: принимает Number или строку (через {@link BigDecimal}).
     *
     * @param o значение из модели фильтра
     * @return числовое значение или {@code null}, если вход {@code null}
     */
    private static Number toNumber(Object o) {
        if (o == null) return null;
        // Если уже Number — возвращаем как есть
        if (o instanceof Number n) return n;
        // Иначе пытаемся распарсить строку через BigDecimal (без потери точности),
        // дальше вызывающий код приведёт к нужному типу.
        return new BigDecimal(o.toString());
    }

}
