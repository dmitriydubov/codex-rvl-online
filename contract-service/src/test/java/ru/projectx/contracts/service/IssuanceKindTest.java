package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.projectx.contracts.support.Tasks;

class IssuanceKindTest {

    @ParameterizedTest
    @CsvSource({
            "false, 0.00, NONE",
            "false, 0.01, KM_CONTRACT",
            "false, 34000.00, KM_CONTRACT",
            "true, 0.00, LM_POLICY",
            "true, 0.01, KM_CONTRACT"})
    void kind_followsRelativeFlagAndPositiveAmountDue(boolean relative, String amountDue, IssuanceKind expected) {
        var participant = relative ? Tasks.spouse(amountDue) : Tasks.insured(amountDue);

        assertThat(IssuanceKind.of(participant)).isEqualTo(expected);
    }
}
