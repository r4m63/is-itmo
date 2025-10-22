package ru.itmo.isitmolab.util;

import lombok.experimental.UtilityClass;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


@UtilityClass // final + static
public final class DateParsers {

    private static final DateTimeFormatter DT_SPACE_SEC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter DT_SPACE_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public static LocalDate parseToLocalDate(String s) {
        if (s == null || s.isBlank())
            return null;

//        "2025-10-20" -> LocalDate.of(2025, 10, 20) - 1
//        "2025-10-20T08:12:45" -> 2025-10-20 - 2
//        "2025-10-20T08:12:45.678" -> 2025-10-20 - 3
//        "2025-10-20 08:12:45.678" -> 2025-10-20 - 4
//        "2025-10-20 08:12:45" -> 2025-10-20 - 5

        try {
            return LocalDate.parse(s);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(s).toLocalDate();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(s, DT_SPACE_MILLIS).toLocalDate();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(s, DT_SPACE_SEC).toLocalDate();
        } catch (Exception ignored) {
        }
        return null;
    }
}
