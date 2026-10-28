package ru.projectx.contracts.entity.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LmPolicyStatusTest {

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
}
