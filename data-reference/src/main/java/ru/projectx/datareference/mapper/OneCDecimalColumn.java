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
 * <p>Разбор читает сообщение из таблицы сырья, где база хранит число цифрами, без порядка, поэтому длинная целая
 * часть обычно приходит сюда сотнями цифр. Длина целой части считается по точности и масштабу {@link BigDecimal}, без
 * разворачивания числа в цифры. Разность считается в {@code long}, а ноль помещается в любую колонку — это защита на
 * случай, если число всё же придёт с порядком: тогда масштаб отрицательный, и в {@code int} разность могла бы
 * переполниться и пропустить такое число, а ноль с порядком ({@code 0E+20}) выглядел бы длинным.
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

    private BigDecimal fitted(BigDecimal value, String field) {
        if (value.signum() != 0 && (long) value.precision() - value.scale() > maxIntegerDigits) {
            throw new OneCFieldException("поле %s больше допустимого".formatted(field));
        }
        return value;
    }
}
