package ru.projectx.datareference.mapper;

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
 * Правила чтения значений из сообщений 1С:УСК, общие для всех потоков: их нарушение не роняет обработку, а тихо
 * портит данные. Нулевой GUID значит «ссылки нет», {@code 0001-01-01} «дата не заполнена», {@code 3000-01-01} открытую
 * дату. Исключения не называют значение: так разбираются и полисы с датой рождения и почтой.
 */
public final class OneCValues {

    private static final LocalDate UNSET_DATE = LocalDate.of(1, 1, 1);
    private static final LocalDate OPEN_DATE = LocalDate.of(3000, 1, 1);
    private static final String NO_REQUIRED_GUID_MESSAGE = "нет обязательного GUID в поле ";
    private static final String NO_REQUIRED_DATE_MESSAGE = "нет обязательной даты в поле ";
    private static final String NO_REQUIRED_TEXT_MESSAGE = "нет обязательного поля ";

    /** GUID поля. Нулевой GUID и пустое поле значат «ссылки нет», и тогда результат пустой. */
    public static Optional<UUID> uuid(JsonNode data, String field) {
        return parsed(data, field, OneCRawParser::uuid, Predicate.not(OneCGuid::isEmpty));
    }

    /** Обязательный GUID, как правило {@code external_id} записи. Его отсутствие нарушает контракт. */
    public static UUID requiredUuid(JsonNode data, String field) {
        return uuid(data, field).orElseThrow(missing(NO_REQUIRED_GUID_MESSAGE, field));
    }

    /** Дата поля. Заглушка и открытая дата дают пустой результат. */
    public static Optional<LocalDate> date(JsonNode data, String field) {
        return parsed(data, field, OneCRawParser::date,
                value -> !UNSET_DATE.equals(value) && !OPEN_DATE.equals(value));
    }

    /**
     * Обязательная дата. Заглушка и открытая дата здесь тоже пустые и отклоняют сообщение: обязательное поле пришло
     * незаполненным, а это не «дата открыта».
     */
    public static LocalDate requiredDate(JsonNode data, String field) {
        return date(data, field).orElseThrow(missing(NO_REQUIRED_DATE_MESSAGE, field));
    }

    /**
     * Строка поля без пробелов по краям, потому что коды 1С дополнены пробелами до длины поля. JSON {@code null}
     * отдельно не проверяется: {@link JsonNode#asString()} в Jackson 3 отдаёт для него пустую строку, а не "null".
     */
    public static Optional<String> text(JsonNode data, String field) {
        return Optional.of(data.path(field))
                .filter(JsonNode::isValueNode)
                .map(value -> value.asString().trim())
                .filter(raw -> !raw.isEmpty());
    }

    /** Обязательная строка. Её отсутствие означает невалидное сообщение, а не пустое значение. */
    public static String requiredText(JsonNode data, String field) {
        return text(data, field).orElseThrow(missing(NO_REQUIRED_TEXT_MESSAGE, field));
    }

    /**
     * Строка, которая обязана поместиться в {@code maxLength} символов. Длинное значение отклоняется здесь, потому что
     * база отвергла бы запись с текстом ошибки, в котором видно само значение.
     */
    public static Optional<String> limitedText(JsonNode data, String field, int maxLength) {
        Optional<String> value = text(data, field);
        if (value.filter(text -> text.length() > maxLength).isPresent()) {
            throw new OneCFieldException("поле %s длиннее %d символов".formatted(field, maxLength));
        }
        return value;
    }

    /** Отсутствующий флаг считается {@code false}, потому что контракт не различает «нет» и «не задано». */
    public static boolean flag(JsonNode data, String field) {
        return data.path(field).asBoolean(false);
    }

    /**
     * Целое число поля; не число JSON даёт пустой результат.
     *
     * @throws OneCFieldException если число записано дробью, в том числе {@code 2.0}, или не помещается в {@code int}
     */
    public static Optional<Integer> integer(JsonNode data, String field) {
        return OneCInteger.read(data, field);
    }

    /**
     * Денежное значение: не больше двух знаков после запятой и тринадцати цифр до неё, иначе база округлила бы его
     * или отказала без имени поля. Сообщения 1С разбираются с дробями в {@link BigDecimal}, потому что в {@code double}
     * число длиннее 15 значащих цифр потеряло бы последний знак ещё до проверки.
     */
    public static Optional<BigDecimal> amount(JsonNode data, String field) {
        return OneCDecimalColumn.amount(data, field);
    }

    /** Разбирает непустой текст поля. Значение, которое не прошло {@code meaningful}, даёт пустой результат. */
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
