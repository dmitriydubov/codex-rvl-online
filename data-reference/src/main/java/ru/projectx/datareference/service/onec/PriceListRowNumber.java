package ru.projectx.datareference.service.onec;

import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.OneCRequiredValues;
import tools.jackson.databind.JsonNode;

/**
 * Номер строки прайса: по нему восстанавливается порядок строк передачи, пришедших разными пакетами.
 *
 * <p>Номер обязателен и должен быть целым числом JSON: текст (даже {@code "2"}) и дробь порядка не задают.
 * Причина отказа называет поле, но не значение.
 */
final class PriceListRowNumber {

    private static final String ROW_NUMBER = "rowNumber";

    private PriceListRowNumber() {
    }

    /** @throws OneCFieldException если номера нет или он не целое число */
    static int of(JsonNode row) {
        return OneCRequiredValues.integer(row, ROW_NUMBER);
    }
}
