package ru.projectx.datareference.service.onec;

import java.math.BigDecimal;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;

/**
 * Обязательные поля сообщения 1С:УСК, которые читаются строго по типу JSON: целое число, логическое значение и число.
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

    /** @throws OneCFieldException если поля нет или оно не целое число JSON в пределах {@code int} */
    public static int integer(JsonNode data, String field) {
        JsonNode value = requirePresent(data, field);
        if (value.isIntegralNumber() && value.canConvertToInt()) {
            return value.intValue();
        }
        throw wrongType(field, "целым числом");
    }

    /** @throws OneCFieldException если поля нет, оно не целое число JSON или оно меньше нуля */
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
     * Число по тому же правилу, что и необязательное денежное значение ({@link OneCValues#amount}).
     *
     * @throws OneCFieldException если поля нет или оно не число JSON
     */
    public static BigDecimal amount(JsonNode data, String field) {
        requirePresent(data, field);
        return OneCValues.amount(data, field).orElseThrow(() -> wrongType(field, "числом"));
    }

    /** @throws OneCFieldException если поля нет, оно не число JSON или оно вне диапазона от 0 до 100 включительно */
    public static BigDecimal percent(JsonNode data, String field) {
        BigDecimal value = amount(data, field);
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
