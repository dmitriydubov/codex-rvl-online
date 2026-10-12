package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.projectx.contracts.entity.CancelReason;

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
}
