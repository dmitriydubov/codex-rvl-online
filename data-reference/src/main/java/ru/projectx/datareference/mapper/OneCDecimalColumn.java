package ru.projectx.datareference.mapper;

import java.math.BigDecimal;
import java.util.Optional;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;

/**
 * Проверка денежного значения из сообщения 1С:УСК по колонке сумм и цен {@code numeric(15,2)}: не больше двух знаков
 * после запятой и не больше тринадцати цифр до неё. У процентов свой предел — диапазон от 0 до 100
 * ({@link OneCRequiredValues#percent}).
 *
 * <p>Значение с целой частью длиннее колонки база не запишет, но в её отказе не видно, какое поле виновато. Поэтому
 * такое значение отклоняется при разборе, с именем поля и без самого значения. Сначала проверяются знаки после запятой
 * ({@link OneCMoneyScale}), потом длина целой части.
 *
 * <p>Разбор читает сообщение из таблицы сырья, где база хранит число цифрами, без порядка, поэтому длинная целая
 * часть обычно приходит сюда сотнями цифр. Длина целой части считается по точности и масштабу {@link BigDecimal}, без
 * разворачивания числа в цифры. Разность считается в {@code long}, а ноль помещается в колонку всегда — это защита на
 * случай, если число всё же придёт с порядком: тогда масштаб отрицательный, и в {@code int} разность могла бы
 * переполниться и пропустить такое число, а ноль с порядком ({@code 0E+20}) выглядел бы длинным.
 */
final class OneCDecimalColumn {

    private static final int AMOUNT_PRECISION = 15;
    private static final int AMOUNT_SCALE = 2;
    private static final long AMOUNT_INTEGER_DIGITS = AMOUNT_PRECISION - AMOUNT_SCALE;

    private OneCDecimalColumn() {
    }

    /**
     * Сумма или цена поля, которая помещается в колонку сумм. Не число JSON — пустой результат.
     *
     * @throws OneCFieldException если у значения больше двух знаков после запятой или целая часть длиннее колонки
     */
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
