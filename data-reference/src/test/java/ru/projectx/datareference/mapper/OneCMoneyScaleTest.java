package ru.projectx.datareference.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.datareference.config.OneCJson;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Колонки денег хранят два знака, и база молча округлила бы лишние, так что сумма разошлась бы с суммой в 1С.
 * Поэтому дробь длиннее двух знаков нарушает контракт. JSON читается настройкой сервиса: от неё зависит,
 * доходит ли до проверки третий знак длинного числа.
 */
class OneCMoneyScaleTest {

    private static final ObjectMapper JSON = OneCJson.mapper();

    /** Нули в конце дроби знаками не считаются: {@code 100.00} и {@code 100.0} — одно значение. */
    @ParameterizedTest
    @ValueSource(strings = {"4100", "4100.5", "4100.55", "4100.550", "4100.5500", "0.01", "1E+3", "1.2345E2"})
    void amountWithinTwoFractionDigits_isReadAsSent(String sent) {
        assertThat(OneCValues.amount(node("price", sent), "price")).contains(new BigDecimal(sent));
    }

    /** В 1С цена хранится с четырьмя знаками, и копейки приходят с хвостом нулей, поэтому знаки считаются по значению. */
    @ParameterizedTest
    @ValueSource(strings = {"1234.5000", "1234.5600", "100.0000"})
    void amountWithFourDigitsEndingInZeros_isReadAsSent(String sent) {
        assertThat(OneCValues.amount(node("price", sent), "price")).contains(new BigDecimal(sent));
    }

    @ParameterizedTest
    @ValueSource(strings = {"4100.551", "0.001", "0.0001", "4100.5501", "1.23456E2", "1234.5670"})
    void amountWithThirdFractionDigit_isContractViolationNamingFieldButNotValue(String sent) {
        assertThatThrownBy(() -> OneCValues.amount(node("price", sent), "price"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле price содержит больше двух знаков после запятой");
    }

    /**
     * В двоичной дроби {@code double} это число стало бы {@code 9999999999999.99}, и проверка его бы не увидела,
     * поэтому шестнадцатая значащая цифра не должна теряться при разборе.
     */
    @Test
    void largestAmountWithThirdFractionDigit_isContractViolation() {
        assertThatThrownBy(() -> OneCValues.amount(node("limit", "9999999999999.991"), "limit"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле limit содержит больше двух знаков после запятой");
    }

    @Test
    void largestAmountWithTwoFractionDigits_isReadExactly() {
        assertThat(OneCValues.amount(node("limit", "9999999999999.99"), "limit"))
                .contains(new BigDecimal("9999999999999.99"));
    }

    /** Масштаб проверяется при чтении числа, раньше диапазона, поэтому причина у сооплаты больше 100 — масштаб. */
    @Test
    void percentWithThirdFractionDigit_isReportedByScaleBeforeRange() {
        assertThatThrownBy(() -> OneCRequiredValues.percent(node("percentCopayment", "100.001"), "percentCopayment"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле percentCopayment содержит больше двух знаков после запятой");
    }

    @Test
    void percentWithTwoFractionDigits_isRead() {
        assertThat(OneCRequiredValues.percent(node("percentCopayment", "99.99"), "percentCopayment"))
                .isEqualTo(new BigDecimal("99.99"));
    }

    private static ObjectNode node(String field, String number) {
        return (ObjectNode) JSON.readTree("{\"%s\":%s}".formatted(field, number));
    }
}
