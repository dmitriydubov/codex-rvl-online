package ru.projectx.datareference.service.onec;

import java.math.BigDecimal;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;

/**
 * Обязательные поля сообщения 1С:УСК, которые читаются строго по типу JSON: целое число, логическое значение и число.
 *
 * <p>Поле без значения или с JSON null — нарушение контракта, а не ноль и не false. Значение другого типа, даже похожее
 * на верное (строка {@code "1"}, число вместо логического), тоже отклоняется: подставленное значение ушло бы соседним
 * сервисам как присланное. Текст отказа называет поле и правило, но не значение.
 */
public final class OneCRequiredValues {

    private static final String NO_REQUIRED_FIELD_MESSAGE = "нет обязательного поля ";

    private OneCRequiredValues() {
    }

    /** @throws OneCFieldException если поля нет или оно не целое число JSON в пределах {@code int} */
    public static int integer(JsonNode data, String field) {
        JsonNode value = present(data, field);
        if (value.isIntegralNumber() && value.canConvertToInt()) {
            return value.intValue();
        }
        throw wrongType(field, "целым числом");
    }

    /** @throws OneCFieldException если поля нет или оно не логическое значение JSON */
    public static boolean flag(JsonNode data, String field) {
        return present(data, field).booleanValueOpt()
                .orElseThrow(() -> wrongType(field, "логическим значением"));
    }

    /**
     * Число читается из его текста, а не через {@code double}: двоичная дробь дала бы копеечные расхождения.
     *
     * @throws OneCFieldException если поля нет или оно не число JSON
     */
    public static BigDecimal amount(JsonNode data, String field) {
        JsonNode value = present(data, field);
        if (value.isNumber()) {
            return new BigDecimal(value.asString());
        }
        throw wrongType(field, "числом");
    }

    private static JsonNode present(JsonNode data, String field) {
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
