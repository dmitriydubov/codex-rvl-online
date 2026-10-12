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

class OneCMoneyScaleTest {

    private static final ObjectMapper JSON = OneCJson.mapper();

    @ParameterizedTest
    @ValueSource(strings = {"4100", "4100.5", "4100.55", "4100.550", "4100.5500", "0.01", "1E+3", "1.2345E2"})
    void amountWithinTwoFractionDigits_isReadAsSent(String sent) {
        assertThat(OneCValues.amount(node("price", sent), "price")).contains(new BigDecimal(sent));
    }

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
