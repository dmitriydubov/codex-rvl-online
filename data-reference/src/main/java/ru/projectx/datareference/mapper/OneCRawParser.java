package ru.projectx.datareference.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import ru.projectx.datareference.exception.OneCFieldException;

final class OneCRawParser {

    static UUID uuid(String raw, String field) {
        return OneCGuid.parse(raw)
                .orElseThrow(() -> new OneCFieldException("поле " + field + " не является GUID"));
    }

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
