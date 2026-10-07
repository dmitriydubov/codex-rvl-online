package ru.projectx.datareference.mapper;

import java.math.BigDecimal;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;

/**
 * Обязательные поля сообщения 1С:УСК, которые читаются строго по типу JSON: целое число, логическое значение и процент.
 *
 * <p>Поле без значения или с JSON null — нарушение контракта, а не ноль и не false. Значение другого типа, даже похожее
 * на верное (строка {@code "1"}, число вместо логического), тоже отклоняется: подставленное значение ушло бы соседним
 * сервисам как присланное. Значение за допустимым пределом отклоняется так же. Текст отказа называет поле и правило,
 * но не значение.
 */
public final class OneCRequiredValues {

    private static final String NO_REQUIRED_FIELD_MESSAGE = "нет обязательного поля ";
    private static final BigDecimal FULL_PERCENT = BigDecimal.valueOf(100);

    private OneCRequiredValues() {
    }

    /**
     * Целое число поля. Дробь проверяется раньше предела, как у необязательных целых полей: дробь за пределом
     * {@code int} — тоже «не является целым числом».
     *
     * @throws OneCFieldException если поля нет, оно не число JSON, записано дробью (в том числе {@code 2.0}) или не
     *                            помещается в {@code int}
     */
    public static int integer(JsonNode data, String field) {
        requirePresent(data, field);
        return OneCInteger.read(data, field).orElseThrow(() -> wrongType(field, "целым числом"));
    }

    /**
     * Целое число поля не меньше нуля. Предел {@code int} проверяется раньше знака: огромное отрицательное число
     * отклоняется как «больше допустимого».
     *
     * @throws OneCFieldException если поля нет, оно не целое число JSON в пределах {@code int} или оно меньше нуля
     */
    public static int nonNegativeInteger(JsonNode data, String field) {
        int value = integer(data, field);
        if (value < 0) {
            throw new OneCFieldException("поле %s отрицательное".formatted(field));
        }
        return value;
    }

    /** @throws OneCFieldException если поля нет или оно не логическое значение JSON */
    public static boolean flag(JsonNode data, String field) {
        return requirePresent(data, field).booleanValueOpt()
                .orElseThrow(() -> wrongType(field, "логическим значением"));
    }

    /**
     * Процент: не больше двух знаков после запятой и от 0 до 100. Знаки проверяются при чтении числа, раньше диапазона.
     * Диапазон строже колонки процента, поэтому и огромное значение отклоняется им.
     *
     * @throws OneCFieldException если поля нет, оно не число JSON, у него больше двух знаков после запятой или оно вне
     *                            диапазона от 0 до 100 включительно
     */
    public static BigDecimal percent(JsonNode data, String field) {
        requirePresent(data, field);
        BigDecimal value = OneCMoneyScale.read(data, field).orElseThrow(() -> wrongType(field, "числом"));
        if (value.signum() < 0 || value.compareTo(FULL_PERCENT) > 0) {
            throw new OneCFieldException("поле %s вне диапазона от 0 до %s".formatted(field, FULL_PERCENT));
        }
        return value;
    }

    private static JsonNode requirePresent(JsonNode data, String field) {
        JsonNode value = data.path(field);
        if (value.isMissingNode() || value.isNull()) {
            throw new OneCFieldException(NO_REQUIRED_FIELD_MESSAGE + field);
        }
        return value;
    }

    private static OneCFieldException wrongType(String field, String expectedType) {
        return new OneCFieldException("поле %s не является %s".formatted(field, expectedType));
    }
}
