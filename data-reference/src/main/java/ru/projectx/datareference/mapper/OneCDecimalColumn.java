package ru.projectx.datareference.mapper;

import java.math.BigDecimal;
import java.util.Optional;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;

/**
 * Проверка суммы из 1С:УСК по колонке {@code numeric(15,2)}: база такое значение не запишет, но из её отказа не видно
 * поля. Длина целой части считается по точности и масштабу без разворачивания в цифры, в {@code long} от переполнения
 * при отрицательном масштабе, а ноль проходит всегда, чтобы {@code 0E+20} не выглядел длинным.
 */
final class OneCDecimalColumn {

    private static final int AMOUNT_PRECISION = 15;
    private static final int AMOUNT_SCALE = 2;
    private static final long AMOUNT_INTEGER_DIGITS = AMOUNT_PRECISION - AMOUNT_SCALE;

    private OneCDecimalColumn() {
    }

    /** @throws OneCFieldException если у значения больше двух знаков после запятой или целая часть длиннее колонки */
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
