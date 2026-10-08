package ru.projectx.datareference.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import ru.projectx.datareference.exception.OneCFieldException;

/**
 * Разбирает запись GUID или даты из 1С:УСК; что значат пустые значения, решает {@link OneCValues}. Текст отказа
 * не содержит значения, потому что им может быть дата рождения из полиса.
 */
final class OneCRawParser {

    /** У отказа нет исключения-причины, чтобы значение не попало и в цепочку причин. */
    static UUID uuid(String raw, String field) {
        return OneCGuid.parse(raw)
                .orElseThrow(() -> new OneCFieldException("поле " + field + " не является GUID"));
    }

    /**
     * Дата приходит со смещением, без него или голой, и календарный день берётся так, как написан. Смещение
     * отбрасывается, а не переводится в UTC: перевод незаметно сдвинул бы полночь +03:00 на предыдущий день.
     */
    static LocalDate date(String raw, String field) {
        try {
            return OffsetDateTime.parse(raw).toLocalDate();
        }
        catch (DateTimeParseException notAnOffsetDateTime) {
            return dateWithoutOffset(raw, field);
        }
    }

    private static LocalDate dateWithoutOffset(String raw, String field) {
        try {
            return LocalDateTime.parse(raw).toLocalDate();
        }
        catch (DateTimeParseException notALocalDateTime) {
            return plainDate(raw, field);
        }
    }

    private static LocalDate plainDate(String raw, String field) {
        try {
            return LocalDate.parse(raw);
        }
        catch (DateTimeParseException notADate) {
            throw new OneCFieldException("поле " + field + " не является датой", notADate);
        }
    }

    private OneCRawParser() {
    }
}
