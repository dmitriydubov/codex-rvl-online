package ru.projectx.datareference.mapper.value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public final class OptionalFields {

    public static @Nullable UUID uuidOrNull(JsonNode data, String field) {
        return OneCValues.uuid(data, field).orElse(null);
    }

    public static @Nullable String textOrNull(JsonNode data, String field) {
        return OneCValues.text(data, field).orElse(null);
    }

    public static @Nullable Integer integerOrNull(JsonNode data, String field) {
        return OneCValues.integer(data, field).orElse(null);
    }

    public static @Nullable LocalDate dateOrNull(JsonNode data, String field) {
        return OneCValues.date(data, field).orElse(null);
    }

    public static @Nullable BigDecimal amountOrNull(JsonNode data, String field) {
        return OneCValues.amount(data, field).orElse(null);
    }

    private OptionalFields() {
    }
}
