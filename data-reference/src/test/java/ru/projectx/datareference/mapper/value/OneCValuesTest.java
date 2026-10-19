package ru.projectx.datareference.mapper.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class OneCValuesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void zeroGuid_meansNoReference() {
        ObjectNode data = node("{\"parentId\":\"00000000-0000-0000-0000-000000000000\"}");

        assertThat(OneCValues.uuid(data, "parentId")).isEmpty();
    }

    @Test
    void realGuid_isRead() {
        ObjectNode data = node("{\"id\":\"f3876c53-c7d8-4620-b364-9538164ba5d4\"}");

        assertThat(OneCValues.uuid(data, "id"))
                .contains(UUID.fromString("f3876c53-c7d8-4620-b364-9538164ba5d4"));
    }

    @Test
    void missingRequiredGuid_isContractViolation() {
        assertThatThrownBy(() -> OneCValues.requiredUuid(node("{}"), "id"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageContaining("id");
    }

    @Test
    void unparsableGuid_isContractViolation() {
        assertThatThrownBy(() -> OneCValues.uuid(node("{\"id\":\"не-гуид\"}"), "id"))
                .isInstanceOf(OneCFieldException.class);
    }

    @Test
    void placeholderDate_isEmpty() {
        assertThat(OneCValues.date(node("{\"beginDate\":\"0001-01-01\"}"), "beginDate")).isEmpty();
    }

    @Test
    void openDate_isEmpty() {
        assertThat(OneCValues.date(node("{\"endDate\":\"3000-01-01\"}"), "endDate")).isEmpty();
    }

    @Test
    void midnightWithOffset_keepsItsCalendarDate() {
        ObjectNode data = node("{\"beginDate\":\"2024-10-10T00:00:00+03:00\"}");

        assertThat(OneCValues.date(data, "beginDate")).contains(LocalDate.of(2024, 10, 10));
    }

    @Test
    void dateTimeWithoutOffset_isRead() {
        assertThat(OneCValues.date(node("{\"dateBeginISO\":\"2026-05-07T00:00:00\"}"), "dateBeginISO"))
                .contains(LocalDate.of(2026, 5, 7));
    }

    @Test
    void endOfDayWithoutOffset_keepsItsCalendarDate() {
        assertThat(OneCValues.date(node("{\"dateEndISO\":\"2027-05-06T23:59:59\"}"), "dateEndISO"))
                .contains(LocalDate.of(2027, 5, 6));
    }

    @Test
    void plainDate_isRead() {
        assertThat(OneCValues.date(node("{\"beginDate\":\"2024-10-10\"}"), "beginDate"))
                .contains(LocalDate.of(2024, 10, 10));
    }

    @Test
    void unparsableDate_isContractViolationNamingFieldWithCauseTypeButNotValue() {
        assertThatThrownBy(() -> OneCValues.date(node("{\"beginDate\":\"10.10.2024\"}"), "beginDate"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле beginDate не является датой")
                .cause()
                .hasMessage("java.time.format.DateTimeParseException")
                .hasNoCause();
    }

    @Test
    void limitedText_withinLimitAfterTrimming_isRead() {
        assertThat(OneCValues.limitedText(node("{\"ИНН\":\" 770000000012 \"}"), "ИНН", 12))
                .contains("770000000012");
    }

    @Test
    void limitedText_overLimit_isContractViolationNamingFieldAndLimit() {
        assertThatThrownBy(() -> OneCValues.limitedText(node("{\"ИНН\":\"7700000000123\"}"), "ИНН", 12))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле ИНН длиннее 12 символов");
    }

    @Test
    void limitedText_missing_isEmpty() {
        assertThat(OneCValues.limitedText(node("{}"), "КПП", 9)).isEmpty();
    }

    @Test
    void requiredDate_rejectsOpenDate() {
        assertThatThrownBy(() -> OneCValues.requiredDate(node("{\"beginDate\":\"3000-01-01\"}"), "beginDate"))
                .isInstanceOf(OneCFieldException.class);
    }

    @Test
    void text_isTrimmed() {
        assertThat(OneCValues.text(node("{\"code\":\"A00   \"}"), "code")).contains("A00");
    }

    @Test
    void blankText_isEmpty() {
        assertThat(OneCValues.text(node("{\"code\":\"   \"}"), "code")).isEmpty();
    }

    @Test
    void missingFlag_isFalse() {
        assertThat(OneCValues.flag(node("{}"), "deleteMark")).isFalse();
    }

    @Test
    void amount_keepsExactDecimal() {
        assertThat(OneCValues.amount(node("{\"price\":4999.95}"), "price"))
                .contains(new BigDecimal("4999.95"));
    }

    @Test
    void amount_isLimitedByMoneyColumn() {
        assertThat(OneCValues.amount(node("{\"price\":9999999999999.99}"), "price"))
                .contains(new BigDecimal("9999999999999.99"));
        assertThatThrownBy(() -> OneCValues.amount(node("{\"price\":10000000000000.00}"), "price"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле price больше допустимого");
    }

    @Test
    void requiredGuid_isRead() {
        ObjectNode data = node("{\"id\":\"f3876c53-c7d8-4620-b364-9538164ba5d4\"}");

        assertThat(OneCValues.requiredUuid(data, "id"))
                .isEqualTo(UUID.fromString("f3876c53-c7d8-4620-b364-9538164ba5d4"));
    }

    @Test
    void requiredGuid_rejectsZeroGuid() {
        ObjectNode data = node("{\"id\":\"00000000-0000-0000-0000-000000000000\"}");

        assertThatThrownBy(() -> OneCValues.requiredUuid(data, "id"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательного GUID в поле id");
    }

    @Test
    void unparsableGuid_namesFieldButNotValue() {
        assertThatThrownBy(() -> OneCValues.uuid(node("{\"guid\":\"не-гуид-42\"}"), "guid"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле guid не является GUID")
                .hasNoCause();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1-2-3-4-5", "000000001-002-0003-0004-000000000005",
            "00000001-0002-0003-0004-+00000000005", "00000001-0002-0003-0004-00000000000\uFF15"})
    void nonCanonicalGuid_isContractViolationNamingFieldButNotValue(String nonCanonical) {
        ObjectNode data = node("{\"contractId\":\"%s\"}".formatted(nonCanonical));

        assertThatThrownBy(() -> OneCValues.requiredUuid(data, "contractId"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле contractId не является GUID")
                .hasNoCause();
    }

    @Test
    void nonCanonicalZeroGuid_isContractViolationRatherThanNoReference() {
        assertThatThrownBy(() -> OneCValues.uuid(node("{\"guid\":\"0-0-0-0-0\"}"), "guid"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле guid не является GUID");
    }

    @Test
    void uppercaseGuid_isTheSameGuid() {
        ObjectNode data = node("{\"id\":\"F3876C53-C7D8-4620-B364-9538164BA5D4\"}");

        assertThat(OneCValues.requiredUuid(data, "id"))
                .isEqualTo(UUID.fromString("f3876c53-c7d8-4620-b364-9538164ba5d4"));
    }

    @Test
    void guidWithSurroundingSpaces_isRead() {
        ObjectNode data = node("{\"id\":\"  f3876c53-c7d8-4620-b364-9538164ba5d4 \"}");

        assertThat(OneCValues.uuid(data, "id")).contains(UUID.fromString("f3876c53-c7d8-4620-b364-9538164ba5d4"));
    }

    @Test
    void requiredDate_isRead() {
        assertThat(OneCValues.requiredDate(node("{\"beginDate\":\"2024-10-10\"}"), "beginDate"))
                .isEqualTo(LocalDate.of(2024, 10, 10));
    }

    @Test
    void missingRequiredDate_namesField() {
        assertThatThrownBy(() -> OneCValues.requiredDate(node("{}"), "beginDate"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательной даты в поле beginDate");
    }

    @Test
    void unparsableDate_namesFieldButNotValue() {
        assertThatThrownBy(() -> OneCValues.date(node("{\"birthDate\":\"17.05.1980\"}"), "birthDate"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле birthDate не является датой");
    }

    @Test
    void missingText_isEmpty() {
        assertThat(OneCValues.text(node("{}"), "code")).isEmpty();
    }

    @Test
    void nullText_isEmpty() {
        assertThat(OneCValues.text(node("{\"code\":null}"), "code")).isEmpty();
    }

    @Test
    void objectInsteadOfText_isEmpty() {
        assertThat(OneCValues.text(node("{\"code\":{\"value\":\"A00\"}}"), "code")).isEmpty();
    }

    @Test
    void numberAsText_isItsTextForm() {
        assertThat(OneCValues.text(node("{\"code\":123}"), "code")).contains("123");
    }

    @Test
    void requiredText_isRead() {
        assertThat(OneCValues.requiredText(node("{\"name\":\" Терапия \"}"), "name")).isEqualTo("Терапия");
    }

    @Test
    void missingRequiredText_namesField() {
        assertThatThrownBy(() -> OneCValues.requiredText(node("{\"name\":\"  \"}"), "name"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательного поля name");
    }

    @Test
    void presentFlag_isRead() {
        assertThat(OneCValues.flag(node("{\"deleteMark\":true}"), "deleteMark")).isTrue();
    }

    @Test
    void integer_isRead() {
        assertThat(OneCValues.integer(node("{\"rowNumber\":7}"), "rowNumber")).contains(7);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2147483647", "-2147483648", "0"})
    void integerAtIntBound_isRead(String sent) {
        assertThat(OneCValues.integer(node("{\"layerNumber\":%s}".formatted(sent)), "layerNumber"))
                .contains(Integer.parseInt(sent));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2147483648", "-2147483649", "10000000000", "-100000000000000000000"})
    void integerBeyondIntBound_isRejectedNamingFieldButNotValue(String sent) {
        ObjectNode data = node("{\"layerNumber\":%s}".formatted(sent));

        assertThatThrownBy(() -> OneCValues.integer(data, "layerNumber"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле layerNumber больше допустимого");
    }

    @Test
    void integerOfThousandDigits_isRejectedNamingField() {
        ObjectNode data = JSON.createObjectNode().put("rowNumber", new BigInteger("1" + "0".repeat(1000)));

        assertThatThrownBy(() -> OneCValues.integer(data, "rowNumber"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле rowNumber больше допустимого");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.5", "-2.5", "2.0", "2.00", "0.0", "25e-1", "3000000000.5"})
    void integerWrittenAsFraction_isRejectedNamingFieldButNotValue(String sent) {
        ObjectNode data = node("{\"layerNumber\":%s}".formatted(sent));

        assertThatThrownBy(() -> OneCValues.integer(data, "layerNumber"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле layerNumber не является целым числом");
    }

    @Test
    void integerWrittenAsDecimalWithZeroFraction_isRejected() {
        ObjectNode data = JSON.createObjectNode().put("hierarchyLevel", new BigDecimal("2.0"));

        assertThatThrownBy(() -> OneCValues.integer(data, "hierarchyLevel"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле hierarchyLevel не является целым числом");
    }

    @Test
    void integerAsText_isEmpty() {
        assertThat(OneCValues.integer(node("{\"rowNumber\":\"7\"}"), "rowNumber")).isEmpty();
    }

    @Test
    void amountAsText_isEmpty() {
        assertThat(OneCValues.amount(node("{\"price\":\"4999.95\"}"), "price")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankGuid_meansNoReference(String blank) {
        assertThat(OneCValues.uuid(node("{\"parentId\":\"%s\"}".formatted(blank)), "parentId")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"3000-01-01T00:00:00+03:00", "3000-01-01T00:00:00",
            "0001-01-01T00:00:00+03:00", "0001-01-01T00:00:00"})
    void placeholderAndOpenDate_asMoment_areEmpty(String unset) {
        assertThat(OneCValues.date(node("{\"endDate\":\"%s\"}".formatted(unset)), "endDate")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0001-01-01", "3000-01-01T00:00:00+03:00"})
    void requiredDate_reportsPlaceholderAndOpenDateAsMissing(String unset) {
        ObjectNode data = node("{\"beginDate\":\"%s\"}".formatted(unset));

        assertThatThrownBy(() -> OneCValues.requiredDate(data, "beginDate"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательной даты в поле beginDate");
    }

    @Test
    void momentWithImpossibleTime_isContractViolation() {
        assertThatThrownBy(() -> OneCValues.date(node("{\"beginDate\":\"2024-10-10T25:00:00\"}"), "beginDate"))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле beginDate не является датой");
    }

    @Test
    void arrayInsteadOfText_isEmpty() {
        assertThat(OneCValues.text(node("{\"code\":[\"A00\"]}"), "code")).isEmpty();
    }

    @Test
    void explicitlyFalseFlag_isFalse() {
        assertThat(OneCValues.flag(node("{\"deleteMark\":false}"), "deleteMark")).isFalse();
    }

    private static ObjectNode node(String json) {
        return (ObjectNode) JSON.readTree(json);
    }
}
