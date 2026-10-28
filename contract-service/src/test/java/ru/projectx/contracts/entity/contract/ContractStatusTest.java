package ru.projectx.contracts.entity.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ContractStatusTest {

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
    @EnumSource(ContractStatus.class)
    void contract_outcomeNamesItsStatusOnceFinished(ContractStatus status) {
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
}
