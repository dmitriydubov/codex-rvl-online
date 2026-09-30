package ru.projectx.datareference.service.onec;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;

/**
 * Правила преобразования значений 1С — раздел «Общие правила преобразования» контракта.
 *
 * <p>Собраны в одном месте, потому что нарушаются они одинаково во всех потоках и каждый
 * раз <b>тихо</b>: неверная дата или незамеченный нулевой GUID не роняют обработку,
 * а оседают в базе неправильным значением, которое обнаружится через месяц.
 *
 * <p>Три ловушки, ради которых этот класс и существует:
 * <ul>
 *   <li>нулевой GUID {@code 00000000-…} означает «ссылки нет», а не «ссылка на объект
 *       с таким идентификатором»;</li>
 *   <li>{@code 0001-01-01} — заглушка «дата не заполнена»;</li>
 *   <li>{@code 3000-01-01} — <b>открытая дата</b>. Сохранить её как есть значит получить
 *       договор, действующий тысячу лет, и фильтр «действующие на сегодня», который
 *       никогда ничего не отсекает.</li>
 * </ul>
 *
 * <p><b>В тексте исключения — имя поля, но не его значение.</b> Эти же правила разбирают
 * полисы, а там значением может оказаться дата рождения или почта. Текст исключения
 * доезжает до лога, и «не разобрана дата 1980-05-17» — это уже персональные данные в
 * логе. Само сообщение при этом целиком лежит в таблице сырья: разобрать случай можно
 * там, не вынося содержимое наружу.
 */
public final class OneCValues {

    private static final UUID ZERO_GUID = new UUID(0L, 0L);
    private static final LocalDate UNSET_DATE = LocalDate.of(1, 1, 1);
    private static final LocalDate OPEN_DATE = LocalDate.of(3000, 1, 1);

    /** GUID поля; нулевой и пустой — «ссылки нет». */
    public static Optional<UUID> uuid(JsonNode data, String field) {
        return text(data, field)
                .flatMap(raw -> parseUuid(raw, field))
                .filter(value -> !ZERO_GUID.equals(value));
    }

    /** Обязательный GUID — как правило, {@code external_id} записи. */
    public static UUID requiredUuid(JsonNode data, String field) {
        return uuid(data, field)
                .orElseThrow(() -> new OneCFieldException("нет обязательного GUID в поле " + field));
    }

    /** Дата поля; заглушка и открытая дата — пусто. */
    public static Optional<LocalDate> date(JsonNode data, String field) {
        return text(data, field)
                .flatMap(raw -> parseDate(raw, field))
                .filter(value -> !UNSET_DATE.equals(value) && !OPEN_DATE.equals(value));
    }

    /**
     * Обязательная дата.
     *
     * <p>Заглушка {@code 0001-01-01} и открытая дата {@code 3000-01-01} пустыми
     * значениями и остаются: поле, объявленное контрактом обязательным, пришло
     * незаполненным — это невалидное сообщение, а не «дата открыта».
     */
    public static LocalDate requiredDate(JsonNode data, String field) {
        return date(data, field)
                .orElseThrow(() -> new OneCFieldException("нет обязательной даты в поле " + field));
    }

    /** Строка с обрезкой пробелов; пустая — пусто (коды приходят дополненными до длины). */
    public static Optional<String> text(JsonNode data, String field) {
        JsonNode value = data.path(field);
        if (!value.isValueNode() || value.isNull()) {
            return Optional.empty();
        }
        String raw = value.asString().trim();
        return raw.isEmpty() ? Optional.empty() : Optional.of(raw);
    }

    /** Обязательная строка: её отсутствие — невалидное сообщение, а не пустое значение. */
    public static String requiredText(JsonNode data, String field) {
        return text(data, field)
                .orElseThrow(() -> new OneCFieldException("нет обязательного поля " + field));
    }

    /** Отсутствующий флаг — это {@code false}: контракт не различает «нет» и «не задано». */
    public static boolean flag(JsonNode data, String field) {
        return data.path(field).asBoolean(false);
    }

    public static Optional<Integer> integer(JsonNode data, String field) {
        JsonNode value = data.path(field);
        return value.isNumber() ? Optional.of(value.asInt()) : Optional.empty();
    }

    /**
     * Денежное значение. Через {@code BigDecimal.valueOf(double)} не идём даже для целых
     * цен: маршрут через двоичную дробь — источник копеечных расхождений, а цена путёвки
     * попадает в расчёт доплаты.
     */
    public static Optional<BigDecimal> amount(JsonNode data, String field) {
        JsonNode value = data.path(field);
        return value.isNumber() ? Optional.of(new BigDecimal(value.asString())) : Optional.empty();
    }

    private static Optional<UUID> parseUuid(String raw, String field) {
        try {
            return Optional.of(UUID.fromString(raw));
        }
        catch (IllegalArgumentException notAGuid) {
            throw new OneCFieldException("поле " + field + " не является GUID", notAGuid);
        }
    }

    /**
     * 1С отдаёт даты моментом со смещением ({@code 2024-10-10T00:00:00+03:00}), но
     * присылает и голую дату.
     *
     * <p>Смещение <b>отбрасывается</b>, а не переводится в UTC: это календарная дата
     * договора, и сдвиг на день назад для полуночи в +03:00 — ровно та ошибка, которую
     * здесь легко не заметить.
     */
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
    private static Optional<LocalDate> parseDate(String raw, String field) {
        try {
            return Optional.of(OffsetDateTime.parse(raw).toLocalDate());
        }
        catch (DateTimeParseException notAnOffsetDateTime) {
            return parseWithoutOffset(raw, field);
        }
    }

    private static Optional<LocalDate> parseWithoutOffset(String raw, String field) {
        try {
            return Optional.of(LocalDateTime.parse(raw).toLocalDate());
        }
        catch (DateTimeParseException notALocalDateTime) {
            return parsePlainDate(raw, field);
        }
    }

    private static Optional<LocalDate> parsePlainDate(String raw, String field) {
        try {
            return Optional.of(LocalDate.parse(raw));
        }
        catch (DateTimeParseException notADate) {
            throw new OneCFieldException("поле " + field + " не является датой", notADate);
        }
    }

    private OneCValues() {
    }
}
