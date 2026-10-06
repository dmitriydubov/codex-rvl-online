package ru.projectx.datareference.mapper;

import java.math.BigDecimal;
import java.util.Optional;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;

/**
 * Колонки, в которых хранятся деньги и проценты из сообщений 1С:УСК, и предел целой части каждой из них.
 *
 * <p>Значение с целой частью длиннее колонки база не запишет, но в её отказе не видно, какое поле виновато. Поэтому
 * такое значение отклоняется при разборе, с именем поля и без самого значения. Сначала проверяются знаки после запятой
 * ({@link OneCMoneyScale}), потом длина целой части.
 *
 * <p>Число с огромным порядком, например {@code 1e999999999}, не разворачивается в цифры: длина целой части считается
 * по точности и масштабу {@link BigDecimal}, иначе миллиард нулей занял бы память и время.
 */
enum OneCDecimalColumn {

    /** Суммы и цены, {@code numeric(15,2)}. */
    AMOUNT(15, 2),

    /** Проценты, {@code numeric(5,2)}. */
    PERCENT(5, 2);

    private final long maxIntegerDigits;

    OneCDecimalColumn(int precision, int scale) {
        this.maxIntegerDigits = precision - scale;
    }

    /**
     * Число поля, которое помещается в колонку. Не число JSON — пустой результат.
     *
     * @throws OneCFieldException если у значения больше двух знаков после запятой или целая часть длиннее колонки
     */
    Optional<BigDecimal> read(JsonNode data, String field) {
        return OneCMoneyScale.read(data, field).map(value -> fitted(value, field));
    }

    /** Масштаб у числа с большим порядком отрицательный, поэтому разность считается в {@code long}, без переполнения. */
    private BigDecimal fitted(BigDecimal value, String field) {
        if ((long) value.precision() - value.scale() > maxIntegerDigits) {
            throw new OneCFieldException("поле %s больше допустимого".formatted(field));
        }
        return value;
    }
}
