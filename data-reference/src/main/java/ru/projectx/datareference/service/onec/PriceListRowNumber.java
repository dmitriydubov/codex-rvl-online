package ru.projectx.datareference.service.onec;

import ru.projectx.datareference.exception.OneCFieldException;
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
        JsonNode value = row.path(ROW_NUMBER);
        if (value.isMissingNode() || value.isNull()) {
            throw new OneCFieldException("нет обязательного поля " + ROW_NUMBER);
        }
        return integral(value);
    }

    private static int integral(JsonNode value) {
        if (value.isIntegralNumber() && value.canConvertToInt()) {
            return value.intValue();
        }
        throw new OneCFieldException("поле " + ROW_NUMBER + " не является целым числом");
    }
}
