package ru.projectx.datareference.service.onec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import ru.projectx.datareference.exception.OneCFieldException;

/**
 * Разбирает непустой текст поля сообщения 1С:УСК в GUID или календарную дату.
 *
 * <p>Здесь проверяется только запись значения. Что значат нулевой GUID, заглушка {@code 0001-01-01}
 * и открытая дата {@code 3000-01-01}, решает {@link OneCValues}. Нечитаемое значение нарушает
 * контракт с 1С:УСК, поэтому разбор отказывает исключением. В его тексте есть имя поля, но нет
 * значения: значением может оказаться дата рождения из полиса.
 */
final class OneCRawParser {

    /**
     * GUID принимается только в канонической записи ({@link OneCGuid}), иначе искажённое значение
     * молча стало бы чужим GUID. У отказа нет исключения-причины, поэтому значение не попадает и в
     * цепочку причин.
     */
    static UUID uuid(String raw, String field) {
        return OneCGuid.parse(raw)
                .orElseThrow(() -> new OneCFieldException("поле " + field + " не является GUID"));
    }

    /**
     * Дата приходит в трёх видах, и все три встречаются в контракте с 1С:УСК: момент со смещением,
     * как в договорах ЛПУ ({@code 2024-10-10T00:00:00+03:00}), момент без смещения, как в договорах
     * страхования ({@code 2026-05-07T00:00:00}), и голая дата ({@code 2024-01-01}).
     *
     * <p>Во всех трёх случаях нужен календарный день, и берётся он <b>так, как написан</b>. Смещение
     * отбрасывается, а не переводится в UTC, потому что это дата договора, а не момент времени.
     * Перевод сдвинул бы полночь со смещением +03:00 на предыдущий день, а такую ошибку легко не
     * заметить.
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
