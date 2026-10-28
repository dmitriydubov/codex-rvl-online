package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.projectx.contracts.entity.issuance.CancelReason;

class CancelReasonsTest {

    @ParameterizedTest
    @CsvSource({
            "CKZ_REJECTED, REQUEST_CANCELLED",
            "HR_REJECTED, REQUEST_CANCELLED",
            "INSURED_REFUSED, INSURED_REFUSED",
            "EXPIRED, EXPIRED",
            "OTHER, OTHER"})
    void code_mapsToDictionaryReason(String code, CancelReason expected) {
        assertThat(CancelReasons.of(code)).contains(expected);
    }

    @Test
    void unknownCode_hasNoReason() {
        assertThat(CancelReasons.of("SOMETHING_ELSE")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "SOMETHING_ELSE",
            "X",
            "A1_B2",
            "CODE_0123456789_0123456789_0123456789_0123456789XX"})
    void upperCaseLatinCodeUpToFiftyCharacters_isWellFormed(String code) {
        assertThat(CancelReasons.wellFormed(code)).contains(code);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "CODE_0123456789_0123456789_0123456789_0123456789XXX",
            "отказ Тестова Т. Т.",
            "something_else",
            "1_CODE",
            "_CODE",
            "CODE-1",
            "CODE ELSE",
            "''"}, emptyValue = "")
    void anythingElse_isNotWellFormed(String code) {
        assertThat(CancelReasons.wellFormed(code)).isEmpty();
    }
}
