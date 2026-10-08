package ru.projectx.datareference.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Необязательные поля сообщения 1С для реквизитов сущностей: пустой результат {@link OneCValues} становится
 * {@code null}, который допускает колонка. Правила чтения остаются в {@link OneCValues}.
 */
final class OptionalFields {

    static @Nullable UUID uuidOrNull(JsonNode data, String field) {
        return OneCValues.uuid(data, field).orElse(null);
    }

    static @Nullable String textOrNull(JsonNode data, String field) {
        return OneCValues.text(data, field).orElse(null);
    }

    static @Nullable Integer integerOrNull(JsonNode data, String field) {
        return OneCValues.integer(data, field).orElse(null);
    }

    static @Nullable LocalDate dateOrNull(JsonNode data, String field) {
        return OneCValues.date(data, field).orElse(null);
    }

    static @Nullable BigDecimal amountOrNull(JsonNode data, String field) {
        return OneCValues.amount(data, field).orElse(null);
    }

    private OptionalFields() {
    }
}
