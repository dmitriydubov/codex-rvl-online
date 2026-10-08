package ru.projectx.datareference.service.onec;

import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.OneCRequiredValues;
import tools.jackson.databind.JsonNode;

/**
 * Номер строки прайса: по нему восстанавливается порядок строк передачи, пришедших разными пакетами. Номер должен
 * быть целым числом JSON в пределах {@code int}, потому что текст (даже {@code "2"}) и дробь порядка не задают.
 */
final class PriceListRowNumber {

    private static final String ROW_NUMBER = "rowNumber";

    private PriceListRowNumber() {
    }

    /** @throws OneCFieldException если номера нет, он не целое число или не помещается в {@code int} */
    static int of(JsonNode row) {
        return OneCRequiredValues.integer(row, ROW_NUMBER);
    }
}
