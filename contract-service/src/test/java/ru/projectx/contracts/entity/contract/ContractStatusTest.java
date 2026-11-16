package ru.projectx.contracts.entity.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.projectx.contracts.entity.issuance.IssuanceFlow;
import ru.projectx.contracts.entity.issuance.IssuanceTransition;

class ContractStatusTest {

    private static final UUID CONTRACT = UUID.fromString("5c2e8f41-7a9b-4d3c-8e1f-2a6b9c0d4e7f");

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

    @Test
    void issuanceFlow_submitsAPaidContractAndNamesTheContractInRefusals() {
        assertThat(ContractStatus.ISSUANCE_FLOW).isEqualTo(new IssuanceFlow<>(ContractStatus.PAID,
                ContractStatus.SUBMITTED, ContractStatus.ERROR, ContractStatus.CANCELLED, "договор КМ"));
    }

    @Test
    void submission_turnsAPaidContractIntoSubmitted() {
        assertThat(IssuanceTransition.SUBMITTED.after(ContractStatus.ISSUANCE_FLOW, ContractStatus.PAID, CONTRACT))
                .isEqualTo(ContractStatus.SUBMITTED);
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = "PAID", mode = EnumSource.Mode.EXCLUDE)
    void submission_isRefusedForAContractThatIsNotPaid(ContractStatus status) {
        assertThatThrownBy(() -> IssuanceTransition.SUBMITTED.after(ContractStatus.ISSUANCE_FLOW, status, CONTRACT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("договор КМ " + CONTRACT + " в статусе " + status + ", а не PAID");
    }

    @ParameterizedTest
    @EnumSource(ContractStatus.class)
    void errorAndCancellation_takeAContractInAnyStatus(ContractStatus status) {
        assertThat(IssuanceTransition.ERROR.after(ContractStatus.ISSUANCE_FLOW, status, CONTRACT))
                .isEqualTo(ContractStatus.ERROR);
        assertThat(IssuanceTransition.CANCELLED.after(ContractStatus.ISSUANCE_FLOW, status, CONTRACT))
                .isEqualTo(ContractStatus.CANCELLED);
    }
}
