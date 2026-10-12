package ru.projectx.datareference.service.onec;

import ru.projectx.datareference.mapper.OneCRequiredValues;
import tools.jackson.databind.JsonNode;

final class PriceListRowNumber {

    private static final String ROW_NUMBER = "rowNumber";

    private PriceListRowNumber() {
    }

    static int of(JsonNode row) {
        return OneCRequiredValues.integer(row, ROW_NUMBER);
    }
}
