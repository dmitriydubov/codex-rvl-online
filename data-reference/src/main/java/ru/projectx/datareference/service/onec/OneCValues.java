package ru.projectx.datareference.service.onec;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
    private static final String NO_REQUIRED_GUID_MESSAGE = "нет обязательного GUID в поле ";
    private static final String NO_REQUIRED_DATE_MESSAGE = "нет обязательной даты в поле ";
    private static final String NO_REQUIRED_TEXT_MESSAGE = "нет обязательного поля ";

    /** GUID поля; нулевой и пустой — «ссылки нет». */
    public static Optional<UUID> uuid(JsonNode data, String field) {
        return parsed(data, field, OneCRawParser::uuid, value -> !ZERO_GUID.equals(value));
    }

    /** Обязательный GUID — как правило, {@code external_id} записи. */
    public static UUID requiredUuid(JsonNode data, String field) {
        return uuid(data, field).orElseThrow(missing(NO_REQUIRED_GUID_MESSAGE, field));
    }

    /** Дата поля; заглушка и открытая дата — пусто. */
    public static Optional<LocalDate> date(JsonNode data, String field) {
        return parsed(data, field, OneCRawParser::date,
                value -> !UNSET_DATE.equals(value) && !OPEN_DATE.equals(value));
    }

    /**
     * Обязательная дата.
     *
     * <p>Заглушка {@code 0001-01-01} и открытая дата {@code 3000-01-01} пустыми
     * значениями и остаются: поле, объявленное контрактом обязательным, пришло
     * незаполненным — это невалидное сообщение, а не «дата открыта».
     */
    public static LocalDate requiredDate(JsonNode data, String field) {
        return date(data, field).orElseThrow(missing(NO_REQUIRED_DATE_MESSAGE, field));
    }

    /**
     * Строка с обрезкой пробелов; пустая — пусто (коды приходят дополненными до длины).
     *
     * <p>Объект, массив и отсутствующее поле — пусто. JSON {@code null} отдельно не
     * проверяется: {@link JsonNode#asString()} в Jackson 3 (сверено по 3.1.4) отдаёт для него
     * пустую строку. В Jackson 2 {@code asText()} давал {@code "null"} — при смене версии
     * это место перепроверить.
     */
    public static Optional<String> text(JsonNode data, String field) {
        return Optional.of(data.path(field))
                .filter(JsonNode::isValueNode)
                .map(value -> value.asString().trim())
                .filter(raw -> !raw.isEmpty());
    }

    /** Обязательная строка: её отсутствие — невалидное сообщение, а не пустое значение. */
    public static String requiredText(JsonNode data, String field) {
        return text(data, field).orElseThrow(missing(NO_REQUIRED_TEXT_MESSAGE, field));
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

    /** Непустой текст поля, разобранный {@code parser}; не прошедшее {@code meaningful} — пусто. */
    private static <T> Optional<T> parsed(JsonNode data, String field,
                                          BiFunction<String, String, T> parser, Predicate<T> meaningful) {
        return text(data, field)
                .map(raw -> parser.apply(raw, field))
                .filter(meaningful);
    }

    /** Текст исключения собирается, только когда значения нет: в нём имя поля, но не значение. */
    private static Supplier<OneCFieldException> missing(String messagePrefix, String field) {
        return () -> new OneCFieldException(messagePrefix + field);
    }

    private OneCValues() {
    }
}
