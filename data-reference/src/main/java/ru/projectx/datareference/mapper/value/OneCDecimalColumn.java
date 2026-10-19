package ru.projectx.datareference.mapper.value;

import java.math.BigDecimal;
import java.util.Optional;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;

final class OneCDecimalColumn {

    private static final int AMOUNT_PRECISION = 15;
    private static final int AMOUNT_SCALE = 2;
    private static final long AMOUNT_INTEGER_DIGITS = AMOUNT_PRECISION - AMOUNT_SCALE;

    private OneCDecimalColumn() {
    }

    static Optional<BigDecimal> amount(JsonNode data, String field) {
        return OneCMoneyScale.read(data, field).map(value -> fitted(value, field));
    }

    private static BigDecimal fitted(BigDecimal value, String field) {
        if (value.signum() != 0 && (long) value.precision() - value.scale() > AMOUNT_INTEGER_DIGITS) {
            throw new OneCFieldException("поле %s больше допустимого".formatted(field));
        }
        return value;
    }
}
