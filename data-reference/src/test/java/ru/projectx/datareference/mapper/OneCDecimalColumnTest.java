package ru.projectx.datareference.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.datareference.config.OneCJson;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Защищает предел целой части денег: значение, которое не поместится в колонку, отклоняется при разборе с
 * именем поля, а не отказом базы, в котором поля не видно.
 *
 * <p>Сообщения разбираются той же настройкой JSON, что и у сервиса: от неё зависит, как читается число с экспонентой.
 */
class OneCDecimalColumnTest {

    private static final ObjectMapper JSON = OneCJson.mapper();
    private static final String TOO_LARGE_PRICE = "поле price больше допустимого";

    @ParameterizedTest
    @ValueSource(strings = {"9999999999999.99", "-9999999999999.99", "9999999999999", "0", "0.01", "1E+12", "0E+20"})
    void amountWithinThirteenIntegerDigits_isReadAsSent(String sent) {
        assertThat(OneCDecimalColumn.amount(node("price", sent), "price")).contains(new BigDecimal(sent));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10000000000000.00", "-10000000000000.00", "10000000000000", "1E+13", "1e999999999"})
    void amountBeyondThirteenIntegerDigits_isRejectedNamingFieldButNotValue(String sent) {
        assertThatThrownBy(() -> OneCDecimalColumn.amount(node("price", sent), "price"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage(TOO_LARGE_PRICE);
    }

    /** Знаки после запятой проверяются первыми: у значения с обоими нарушениями причина — знаки. */
    @Test
    void valueBreakingBothRules_isReportedByScale() {
        assertThatThrownBy(() -> OneCDecimalColumn.amount(node("price", "10000000000000.001"), "price"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле price содержит больше двух знаков после запятой");
    }

    /**
     * Порядок у самой границы {@code int}: число цифр целой части не переполняется и не становится отрицательным, так
     * что значение не проскакивает проверку.
     */
    @Test
    void exponentAtIntLimit_isRejected() {
        assertThatThrownBy(() -> OneCDecimalColumn.amount(node("price", "1e2147483647"), "price"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage(TOO_LARGE_PRICE);
    }

    /**
     * Число с огромным порядком нельзя разворачивать в цифры: миллиард нулей занял бы память и время. Проверка
     * сравнивает только длину целой части, и запас по времени у неё многократный.
     */
    @Test
    void hugeExponent_isRejectedWithoutExpandingDigits() {
        ObjectNode data = node("price", "1e999999999");
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertThatThrownBy(
                () -> OneCDecimalColumn.amount(data, "price")).isInstanceOf(OneCFieldException.class));
    }

    /**
     * Самое длинное число, которое база может вернуть из сырья: 131072 цифры до запятой и 16383 нуля после. Нули дроби
     * не снимаются по одному, а значение не разбирается заново из текста, поэтому отказ занимает доли секунды, а не
     * десятки секунд.
     */
    @Test
    void longestStoredNumber_isRejectedQuickly() {
        ObjectNode data = JSON.createObjectNode()
                .put("price", new BigDecimal("1" + "0".repeat(131_071) + "." + "0".repeat(16_383)));
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThatThrownBy(
                () -> OneCDecimalColumn.amount(data, "price"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage(TOO_LARGE_PRICE));
    }

    /**
     * Диапазон сооплаты строже колонки процента, поэтому и огромное значение отклоняется им, а не пределом колонки:
     * причина одна для всех значений больше 100 или меньше 0.
     */
    @ParameterizedTest
    @ValueSource(strings = {"1E+3", "1e999999999", "-1e999999999"})
    void requiredPercentFarBeyondColumn_isRejectedByRange(String sent) {
        assertThatThrownBy(() -> OneCRequiredValues.percent(node("percentCopayment", sent), "percentCopayment"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле percentCopayment вне диапазона от 0 до 100");
    }

    /**
     * Так до разбора доходит {@code 1e500}: сырьё хранится в {@code jsonb}, и база отдаёт число цифрами, без порядка.
     * Пятьсот одна цифра — в пределах длины числа, которую читает разбор JSON.
     */
    @Test
    void longPlainNumberFromStoredPayload_isRejected() {
        assertThatThrownBy(() -> OneCDecimalColumn.amount(node("price", "1" + "0".repeat(500)), "price"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage(TOO_LARGE_PRICE);
    }

    @Test
    void absentOrTextValue_isEmpty() {
        assertThat(OneCDecimalColumn.amount(node("price", "\"1e999999999\""), "price")).isEmpty();
        assertThat(OneCDecimalColumn.amount(node("other", "1"), "price")).isEmpty();
    }

    private static ObjectNode node(String field, String number) {
        return (ObjectNode) JSON.readTree("{\"%s\":%s}".formatted(field, number));
    }
}
