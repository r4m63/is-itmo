package ru.itmo.isitmolab.util.gridtable;

import lombok.experimental.UtilityClass;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Утилиты парсинга дат для фильтров грид-таблицы.
 *
 * <h3>Назначение</h3>
 * Преобразовать входные строковые значения (из UI/запросов) к {@link LocalDate} с поддержкой
 * нескольких распространённых форматов без привязки к часовому поясу.
 *
 * <h3>Поддерживаемые форматы (порядок проверки)</h3>
 * <ol>
 *   <li><b>ISO_LOCAL_DATE</b>: {@code yyyy-MM-dd}, например {@code 2025-10-10}.</li>
 *   <li><b>ISO_LOCAL_DATE_TIME</b>: {@code yyyy-MM-dd'T'HH:mm[:ss[.SSS]]},
 *       например {@code 2025-10-10T14:30} или {@code 2025-10-10T14:30:59.123};
 *       берётся только календарная дата (часть времени отбрасывается).</li>
 *   <li><b>yyyy-MM-dd HH:mm:ss.SSS</b> — пробел между датой и временем, миллисекунды.</li>
 *   <li><b>yyyy-MM-dd HH:mm:ss</b> — пробел, без миллисекунд.</li>
 * </ol>
 *
 * <h3>Поведение</h3>
 * <ul>
 *   <li>Возвращает {@code null}, если строка пуста/некорректна под все перечисленные форматы.</li>
 *   <li>Парсинг выполняется без учёта таймзоны: получаем {@link LocalDate}, а не {@code Instant/ZonedDateTime}.</li>
 *   <li>{@link DateTimeFormatter} из пакета java.time потокобезопасен — статические константы можно переиспользовать.</li>
 * </ul>
 *
 * <h3>Примеры</h3>
 * <pre>
 * parseToLocalDate("2025-02-01")                  -> 2025-02-01
 * parseToLocalDate("2025-02-01T23:59:59.999")     -> 2025-02-01
 * parseToLocalDate("2025-02-01 23:59:59.123")     -> 2025-02-01
 * parseToLocalDate("2025-02-01 23:59:59")         -> 2025-02-01
 * parseToLocalDate("01/02/2025")                  -> null   // не поддерживается
 * </pre>
 *
 * <h3>Рекомендации для продакшена</h3>
 * <ul>
 *   <li>Если требуется поддержка часовых поясов — добавьте парсинг ISO_INSTANT/ZonedDateTime,
 *       затем конвертируйте в локальную дату по нужной TZ.</li>
 *   <li>Если нужен строгий контроль формата, сократите список допустимых шаблонов
 *       или валидируйте входные строки до вызова парсера.</li>
 * </ul>
 */
@UtilityClass
public final class DateParsers {

    /** yyyy-MM-dd HH:mm:ss */
    private static final DateTimeFormatter DT_SPACE_SEC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** yyyy-MM-dd HH:mm:ss.SSS */
    private static final DateTimeFormatter DT_SPACE_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Пытается распарсить строку в {@link LocalDate}, перебирая несколько форматов.
     *
     * <p>Алгоритм: быстрый путь через {@link LocalDate#parse(CharSequence)} (ISO),
     * затем попытки распарсить как {@link LocalDateTime} в ISO и в двух «пробельных» форматах.
     * При успешном парсинге время отбрасывается ({@code toLocalDate()}).</p>
     *
     * @param s входная строка с датой/датой-временем; допускаются см. список форматов выше
     * @return {@link LocalDate} или {@code null}, если формат не распознан/строка пустая
     */
    public static LocalDate parseToLocalDate(String s) {
        if (s == null || s.isBlank()) return null;

        try {
            // ISO_LOCAL_DATE, например 2025-10-10
            return LocalDate.parse(s);
        } catch (Exception ignored) {
            // продолжаем пробовать другие форматы
        }

        try {
            // ISO_LOCAL_DATE_TIME, например 2025-10-10T14:30[:ss[.SSS]]
            return LocalDateTime.parse(s).toLocalDate();
        } catch (Exception ignored) {
        }

        try {
            // yyyy-MM-dd HH:mm:ss.SSS
            return LocalDateTime.parse(s, DT_SPACE_MILLIS).toLocalDate();
        } catch (Exception ignored) {
        }

        try {
            // yyyy-MM-dd HH:mm:ss
            return LocalDateTime.parse(s, DT_SPACE_SEC).toLocalDate();
        } catch (Exception ignored) {
        }

        // Ни один формат не подошёл
        return null;
    }
}
