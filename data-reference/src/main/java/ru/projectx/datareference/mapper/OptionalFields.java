package ru.projectx.datareference.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Необязательные поля сообщения 1С в том виде, в каком их принимают реквизиты записей справочников:
 * пустое значение становится {@code null}, потому что колонка в базе допускает его.
 *
 * <p>Сами правила чтения, например нулевой GUID или строка из одних пробелов как пустое значение,
 * остаются в {@link OneCValues}; здесь только переход от пустого результата к {@code null}.
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
