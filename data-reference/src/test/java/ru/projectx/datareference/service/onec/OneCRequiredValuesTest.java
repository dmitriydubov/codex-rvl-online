package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Защищает чтение обязательных полей сообщения 1С:УСК строго по типу JSON: целое число, логическое значение и число.
 *
 * <p>Поле, которого нет, и поле с JSON null — нарушение контракта, а не ноль и не false. Значение, похожее на верное,
 * но другого типа (строка {@code "1"}, число {@code 1} вместо логического), тоже отклоняется: иначе разбор снова
 * подставлял бы условия, которых источник не присылал. Причина отказа называет поле и правило, но не значение.
 */
class OneCRequiredValuesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"layer\":null}"})
    void absentInteger_isMissingField(String json) {
        assertThatThrownBy(() -> OneCRequiredValues.integer(node(json), "layer"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательного поля layer");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"7\"", "7.5", "true", "3000000000", "{}"})
    void integerOfAnotherType_isRejectedWithoutValue(String value) {
        assertThatThrownBy(() -> OneCRequiredValues.integer(node("{\"layer\":" + value + "}"), "layer"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле layer не является целым числом");
    }

    @Test
    void integralNumber_isRead() {
        assertThat(OneCRequiredValues.integer(node("{\"layer\":7}"), "layer")).isEqualTo(7);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"direct\":null}"})
    void absentFlag_isMissingFieldRatherThanFalse(String json) {
        assertThatThrownBy(() -> OneCRequiredValues.flag(node(json), "direct"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательного поля direct");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"true\"", "1", "0"})
    void flagOfAnotherType_isRejectedWithoutValue(String value) {
        assertThatThrownBy(() -> OneCRequiredValues.flag(node("{\"direct\":" + value + "}"), "direct"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле direct не является логическим значением");
    }

    @Test
    void booleanFlag_isReadAsSent() {
        assertThat(OneCRequiredValues.flag(node("{\"direct\":true}"), "direct")).isTrue();
        assertThat(OneCRequiredValues.flag(node("{\"direct\":false}"), "direct")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"share\":null}"})
    void absentAmount_isMissingField(String json) {
        assertThatThrownBy(() -> OneCRequiredValues.amount(node(json), "share"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательного поля share");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"10.00\"", "true", "[]"})
    void amountOfAnotherType_isRejectedWithoutValue(String value) {
        assertThatThrownBy(() -> OneCRequiredValues.amount(node("{\"share\":" + value + "}"), "share"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле share не является числом");
    }

    @Test
    void amount_isReadFromNumberTextWithoutBinaryRounding() {
        assertThat(OneCRequiredValues.amount(node("{\"share\":12.34}"), "share")).isEqualByComparingTo("12.34");
        assertThat(OneCRequiredValues.amount(node("{\"share\":0}"), "share")).isZero();
    }

    private static ObjectNode node(String json) {
        return (ObjectNode) JSON.readTree(json);
    }
}
