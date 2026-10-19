package ru.projectx.datareference.mapper.value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;

public final class OneCValues {

    private static final LocalDate UNSET_DATE = LocalDate.of(1, 1, 1);
    private static final LocalDate OPEN_DATE = LocalDate.of(3000, 1, 1);
    private static final String NO_REQUIRED_GUID_MESSAGE = "нет обязательного GUID в поле ";
    private static final String NO_REQUIRED_DATE_MESSAGE = "нет обязательной даты в поле ";
    private static final String NO_REQUIRED_TEXT_MESSAGE = "нет обязательного поля ";

    public static Optional<UUID> uuid(JsonNode data, String field) {
        return parsed(data, field, OneCRawParser::uuid, Predicate.not(OneCGuid::isEmpty));
    }

    public static UUID requiredUuid(JsonNode data, String field) {
        return uuid(data, field).orElseThrow(missing(NO_REQUIRED_GUID_MESSAGE, field));
    }

    public static Optional<LocalDate> date(JsonNode data, String field) {
        return parsed(data, field, OneCRawParser::date,
                value -> !UNSET_DATE.equals(value) && !OPEN_DATE.equals(value));
    }

    public static LocalDate requiredDate(JsonNode data, String field) {
        return date(data, field).orElseThrow(missing(NO_REQUIRED_DATE_MESSAGE, field));
    }

    public static Optional<String> text(JsonNode data, String field) {
        return Optional.of(data.path(field))
                .filter(JsonNode::isValueNode)
                .map(value -> value.asString().trim())
                .filter(raw -> !raw.isEmpty());
    }

    public static String requiredText(JsonNode data, String field) {
        return text(data, field).orElseThrow(missing(NO_REQUIRED_TEXT_MESSAGE, field));
    }

    public static Optional<String> limitedText(JsonNode data, String field, int maxLength) {
        Optional<String> value = text(data, field);
        if (value.filter(text -> text.length() > maxLength).isPresent()) {
            throw new OneCFieldException("поле %s длиннее %d символов".formatted(field, maxLength));
        }
        return value;
    }

    public static boolean flag(JsonNode data, String field) {
        return data.path(field).asBoolean(false);
    }

    public static Optional<Integer> integer(JsonNode data, String field) {
        return OneCInteger.read(data, field);
    }

    public static Optional<BigDecimal> amount(JsonNode data, String field) {
        return OneCDecimalColumn.amount(data, field);
    }

    private static <T> Optional<T> parsed(JsonNode data, String field,
                                          BiFunction<String, String, T> parser, Predicate<T> meaningful) {
        return text(data, field)
                .map(raw -> parser.apply(raw, field))
                .filter(meaningful);
    }

    private static Supplier<OneCFieldException> missing(String messagePrefix, String field) {
        return () -> new OneCFieldException(messagePrefix + field);
    }

    private OneCValues() {
    }
}
