package ru.projectx.datareference.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;

/**
 * Не больше двух знаков после запятой у денег и процентов из 1С: база молча округлила бы лишние, и сумма разошлась
 * бы с 1С. Нули в конце дроби знаками не считаются. Отказ не называет значение, потому что так читаются и суммы полиса.
 */
final class OneCMoneyScale {

    private static final int MAX_FRACTION_DIGITS = 2;

    private OneCMoneyScale() {
    }

    /**
     * Число поля без предела целой части; не число JSON даёт пустой результат. Значение берётся как его прочитал
     * разбор JSON в {@link BigDecimal}, без повторного разбора текста, поэтому запись совпадает с присланной.
     */
    static Optional<BigDecimal> read(JsonNode data, String field) {
        JsonNode value = data.path(field);
        return value.isNumber()
                ? Optional.of(checked(value.decimalValue(), field))
                : Optional.empty();
    }

    /**
     * Лишние знаки ищутся одним отбрасыванием хвоста, а не снятием нулей по одному: у числа из сотен тысяч цифр это
     * заняло бы секунды. Число с масштабом не больше двух не пересчитывается, иначе огромный порядок развернулся бы.
     */
    private static BigDecimal checked(BigDecimal amount, String field) {
        int keptScale = Math.min(amount.scale(), MAX_FRACTION_DIGITS);
        if (amount.compareTo(amount.setScale(keptScale, RoundingMode.DOWN)) != 0) {
            throw new OneCFieldException("поле %s содержит больше двух знаков после запятой".formatted(field));
        }
        return amount;
    }
}
