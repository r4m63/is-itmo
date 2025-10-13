package ru.itmo.isitmolab.util.gridtable;

import lombok.experimental.UtilityClass;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


@UtilityClass
public final class DateParsers {

    private static final DateTimeFormatter DT_SPACE_SEC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter DT_SPACE_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public static LocalDate parseToLocalDate(String s) {
        if (s == null || s.isBlank()) return null;

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
