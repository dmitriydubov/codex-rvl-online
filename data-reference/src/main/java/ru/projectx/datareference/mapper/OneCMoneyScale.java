package ru.projectx.datareference.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;

final class OneCMoneyScale {

    private static final int MAX_FRACTION_DIGITS = 2;

    private OneCMoneyScale() {
    }

    static Optional<BigDecimal> read(JsonNode data, String field) {
        JsonNode value = data.path(field);
        return value.isNumber()
                ? Optional.of(checked(value.decimalValue(), field))
                : Optional.empty();
    }

    private static BigDecimal checked(BigDecimal amount, String field) {
        int keptScale = Math.min(amount.scale(), MAX_FRACTION_DIGITS);
        if (amount.compareTo(amount.setScale(keptScale, RoundingMode.DOWN)) != 0) {
            throw new OneCFieldException("поле %s содержит больше двух знаков после запятой".formatted(field));
        }
        return amount;
    }
}
