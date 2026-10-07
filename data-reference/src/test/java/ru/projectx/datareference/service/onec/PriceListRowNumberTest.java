package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Защищает правило номера строки прайса: по нему восстанавливается порядок строк передачи, поэтому номер
 * обязателен и целый, а причина отказа называет поле, но не значение.
 */
class PriceListRowNumberTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @ParameterizedTest
    @ValueSource(strings = {"1", "0", "-3", "2147483647"})
    void integralNumber_isTheRowNumber(String number) {
        assertThat(PriceListRowNumber.of(row("{\"rowNumber\":" + number + "}"))).isEqualTo(Integer.parseInt(number));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"rowNumber\":null}"})
    void absentRowNumber_isMissingField(String row) {
        assertThatThrownBy(() -> PriceListRowNumber.of(row(row)))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательного поля rowNumber");
    }

    /** Текст и дробь, в том числе за пределом {@code int}, — не номер строки; значение в причину не попадает. */
    @ParameterizedTest
    @ValueSource(strings = {"\"вторая\"", "\"2\"", "2.5", "2.0", "10000000000.5", "true"})
    void nonIntegralRowNumber_isNotAnInteger(String value) {
        assertThatThrownBy(() -> PriceListRowNumber.of(row("{\"rowNumber\":" + value + "}")))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле rowNumber не является целым числом");
    }

    /** Целое шире {@code int} — номер строки больше допустимого; значение в причину не попадает. */
    @ParameterizedTest
    @ValueSource(strings = {"2147483648", "10000000000", "-2147483649"})
    void rowNumberBeyondInt_isTooLarge(String value) {
        assertThatThrownBy(() -> PriceListRowNumber.of(row("{\"rowNumber\":" + value + "}")))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле rowNumber больше допустимого");
    }

    @Test
    void otherFieldsOfTheRow_doNotMatter() {
        assertThat(PriceListRowNumber.of(row("{\"price\":\"много\",\"rowNumber\":7}"))).isEqualTo(7);
    }

    private static JsonNode row(String text) {
        return JSON.readTree(text);
    }
}
