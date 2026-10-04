package ru.projectx.datareference.service.onec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import ru.projectx.datareference.exception.OneCFieldException;

/**
 * Разбор непустого текста поля 1С в GUID и календарную дату.
 *
 * <p>Здесь только синтаксис. Что значат нулевой GUID, заглушка {@code 0001-01-01} и открытая
 * дата {@code 3000-01-01}, решает {@link OneCValues}. Нечитаемое значение — нарушение контракта:
 * в тексте исключения имя поля, но не значение (см. {@link OneCValues}).
 */
final class OneCRawParser {

    /**
     * GUID — только в канонической форме ({@link OneCGuid}): иначе искажённое значение молча
     * стало бы чужим GUID. У отказа нет причины-исключения, поэтому значение не попадает и в цепочку
     * причин.
     */
    static UUID uuid(String raw, String field) {
        return OneCGuid.parse(raw)
                .orElseThrow(() -> new OneCFieldException("поле " + field + " не является GUID"));
    }

    /**
     * Дата приходит в трёх видах, и все три встречаются в контракте:
     * момент со смещением ({@code 2024-10-10T00:00:00+03:00}, ТЗ-5),
     * момент без смещения ({@code 2026-05-07T00:00:00}, ТЗ-2) и голая дата
     * ({@code 2024-01-01}).
     *
     * <p>Во всех трёх нужен календарный день, и берётся он <b>как написан</b>. Смещение
     * отбрасывается, а не переводится в UTC: это дата договора, а не момент времени,
     * и сдвиг полуночи в +03:00 на день назад — ровно та ошибка, которую не замечают.
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
