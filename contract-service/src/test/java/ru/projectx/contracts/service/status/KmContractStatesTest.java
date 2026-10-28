package ru.projectx.contracts.service.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.policy.LmPolicyStatus;
import ru.projectx.contracts.message.KmContractState;

class KmContractStatesTest {

    @ParameterizedTest
    @CsvSource({
            "DRAFT_READY, DRAFTED", "ACCEPTED, DRAFTED", "INVOICE_ISSUED, DRAFTED", "PAID, DRAFTED",
            "SUBMITTED, DRAFTED", "ISSUED, ISSUED", "ERROR, CANCELLED", "CANCELLED, CANCELLED"})
    void state_followsTheFurthestMilestoneOfTheContract(ContractStatus status, KmContractState expected) {
        assertThat(KmContractStates.of(status)).isEqualTo(expected);
    }

    @Test
    void contractWithoutDraft_hasNoStateInTheContract() {
        assertThatThrownBy(() -> KmContractStates.of(ContractStatus.NEW)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("без проекта");
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = "NEW", mode = EnumSource.Mode.EXCLUDE)
    void onlyNew_isUndrafted(ContractStatus status) {
        assertThat(KmContractStates.undrafted(status)).isFalse();
        assertThat(KmContractStates.undrafted(ContractStatus.NEW)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = "DRAFT_READY", mode = EnumSource.Mode.EXCLUDE)
    void onlyDraftReady_isReportedOnEntry(ContractStatus status) {
        assertThat(KmContractStates.reportedOnEntry(status)).isFalse();
        assertThat(KmContractStates.reportedOnEntry(ContractStatus.DRAFT_READY)).isTrue();
    }

    @Test
    void lmPolicyStatus_isNeverReportedOnEntryAsAContractState() {
        assertThat(KmContractStates.reportedOnEntry(LmPolicyStatus.NEW)).isFalse();
    }
}
