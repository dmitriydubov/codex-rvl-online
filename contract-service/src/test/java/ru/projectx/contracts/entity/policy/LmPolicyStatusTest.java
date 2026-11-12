package ru.projectx.contracts.entity.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.projectx.contracts.entity.issuance.IssuanceFlow;
import ru.projectx.contracts.entity.issuance.IssuanceTransition;

class LmPolicyStatusTest {

    private static final UUID POLICY = UUID.fromString("9e4d1c7b-2f8a-4b6e-a3d5-7c0e1f2a8b94");

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
    @EnumSource(LmPolicyStatus.class)
    void lmPolicy_outcomeNamesItsStatusOnceFinished(LmPolicyStatus status) {
        assertThat(status.outcome().name().equals(status.name())).isEqualTo(status.finished());
    }

    @ParameterizedTest
    @EnumSource(LmPolicyStatus.class)
    void lmPolicy_isIssuedOnlyWhenIssuedAndInErrorOnlyWhenError(LmPolicyStatus status) {
        assertThat(status.issued()).isEqualTo(status == LmPolicyStatus.ISSUED);
        assertThat(status.error()).isEqualTo(status == LmPolicyStatus.ERROR);
        assertThat(status.stopped())
                .isEqualTo(status == LmPolicyStatus.ERROR || status == LmPolicyStatus.CANCELLED);
    }

    @Test
    void issuanceFlow_submitsANewPolicyAndNamesThePolicyInRefusals() {
        assertThat(LmPolicyStatus.ISSUANCE_FLOW).isEqualTo(new IssuanceFlow<>(LmPolicyStatus.NEW,
                LmPolicyStatus.SUBMITTED, LmPolicyStatus.ERROR, LmPolicyStatus.CANCELLED, "полис ЛМ"));
    }

    @Test
    void submission_turnsANewPolicyIntoSubmitted() {
        assertThat(IssuanceTransition.SUBMITTED.after(LmPolicyStatus.ISSUANCE_FLOW, LmPolicyStatus.NEW, POLICY))
                .isEqualTo(LmPolicyStatus.SUBMITTED);
    }

    @ParameterizedTest
    @EnumSource(value = LmPolicyStatus.class, names = "NEW", mode = EnumSource.Mode.EXCLUDE)
    void submission_isRefusedForAPolicyThatIsNotNew(LmPolicyStatus status) {
        assertThatThrownBy(() -> IssuanceTransition.SUBMITTED.after(LmPolicyStatus.ISSUANCE_FLOW, status, POLICY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("полис ЛМ " + POLICY + " в статусе " + status + ", а не NEW");
    }

    @ParameterizedTest
    @EnumSource(LmPolicyStatus.class)
    void errorAndCancellation_takeAPolicyInAnyStatus(LmPolicyStatus status) {
        assertThat(IssuanceTransition.ERROR.after(LmPolicyStatus.ISSUANCE_FLOW, status, POLICY))
                .isEqualTo(LmPolicyStatus.ERROR);
        assertThat(IssuanceTransition.CANCELLED.after(LmPolicyStatus.ISSUANCE_FLOW, status, POLICY))
                .isEqualTo(LmPolicyStatus.CANCELLED);
    }
}
