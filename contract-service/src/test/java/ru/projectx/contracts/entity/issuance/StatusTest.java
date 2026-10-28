package ru.projectx.contracts.entity.issuance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.entity.LmPolicyStatus;

class StatusTest {

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"ISSUED", "ERROR", "CANCELLED"})
    void contract_isFinishedOnceIssuedFailedOrCancelled(ContractStatus status) {
        assertThat(status.finished()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"ISSUED", "ERROR", "CANCELLED"}, mode = EnumSource.Mode.EXCLUDE)
    void contract_isUnfinishedWhileWorkContinues(ContractStatus status) {
        assertThat(status.finished()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = LmPolicyStatus.class, names = {"ISSUED", "ERROR", "CANCELLED"})
    void lmPolicy_isFinishedOnceIssuedFailedOrCancelled(LmPolicyStatus status) {
        assertThat(status.finished()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = LmPolicyStatus.class, names = {"NEW", "SUBMITTED"})
    void lmPolicy_isUnfinishedWhileWorkContinues(LmPolicyStatus status) {
        assertThat(status.finished()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ContractStatus.class)
    void contract_outcomeNamesItsStatusOnceFinished(ContractStatus status) {
        assertThat(status.outcome().name().equals(status.name())).isEqualTo(status.finished());
    }

    @ParameterizedTest
    @EnumSource(LmPolicyStatus.class)
    void lmPolicy_outcomeNamesItsStatusOnceFinished(LmPolicyStatus status) {
        assertThat(status.outcome().name().equals(status.name())).isEqualTo(status.finished());
    }

    @ParameterizedTest
    @EnumSource(ContractStatus.class)
    void contract_isIssuedOnlyWhenIssuedAndInErrorOnlyWhenError(ContractStatus status) {
        assertThat(status.issued()).isEqualTo(status == ContractStatus.ISSUED);
        assertThat(status.error()).isEqualTo(status == ContractStatus.ERROR);
        assertThat(status.stopped())
                .isEqualTo(status == ContractStatus.ERROR || status == ContractStatus.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(LmPolicyStatus.class)
    void lmPolicy_isIssuedOnlyWhenIssuedAndInErrorOnlyWhenError(LmPolicyStatus status) {
        assertThat(status.issued()).isEqualTo(status == LmPolicyStatus.ISSUED);
        assertThat(status.error()).isEqualTo(status == LmPolicyStatus.ERROR);
        assertThat(status.stopped())
                .isEqualTo(status == LmPolicyStatus.ERROR || status == LmPolicyStatus.CANCELLED);
    }
}
