package ru.projectx.datareference.mapper;

import java.math.BigDecimal;
import java.util.Optional;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;

/**
 * Правило знаков после запятой у денежных значений и процентов из сообщений 1С:УСК: не больше двух.
 *
 * <p>Колонки денег и процентов хранят два знака после запятой, и база молча округлила бы лишние, так что сумма у нас
 * разошлась бы с суммой в 1С. Нули в конце дроби знаками не считаются: {@code 100.00} и {@code 100.0} — одно значение.
 * Отказ называет поле и правило, но не значение: этим же правилом читаются суммы полиса.
 */
final class OneCMoneyScale {

    private static final int MAX_FRACTION_DIGITS = 2;

    private OneCMoneyScale() {
    }

    /**
     * Число поля по правилу знаков, без предела целой части. Не число JSON — пустой результат.
     *
     * <p>Значение берётся из десятичной записи числа. Сообщения 1С разбираются с дробями в {@link BigDecimal}, и
     * запись совпадает с присланной; число с порядком остаётся записанным с порядком и не разворачивается.
     */
    static Optional<BigDecimal> read(JsonNode data, String field) {
        JsonNode value = data.path(field);
        return value.isNumber()
                ? Optional.of(checked(new BigDecimal(value.asString()), field))
                : Optional.empty();
    }

    /** @throws OneCFieldException если у значения больше двух значащих знаков после запятой */
    private static BigDecimal checked(BigDecimal amount, String field) {
        if (amount.stripTrailingZeros().scale() > MAX_FRACTION_DIGITS) {
            throw new OneCFieldException("поле %s содержит больше двух знаков после запятой".formatted(field));
        }
        return amount;
    }
}
