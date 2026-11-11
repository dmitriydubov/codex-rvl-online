package ru.projectx.contracts.entity.issuance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IssuanceTransitionTest {

    private static final UUID RECORD = UUID.fromString("0b7a6a3e-5d1c-4f6e-9a2b-3c4d5e6f7a8b");
    private static final IssuanceFlow<SampleRecordStatus> FLOW = new IssuanceFlow<>(SampleRecordStatus.READY,
            SampleRecordStatus.SENT, SampleRecordStatus.FAILED, SampleRecordStatus.STOPPED, "запись");

    @Test
    void submission_movesARecordFromTheStatusItIsSubmittedFromIntoSubmitted() {
        assertThat(IssuanceTransition.SUBMITTED.after(FLOW, SampleRecordStatus.READY, RECORD))
                .isEqualTo(SampleRecordStatus.SENT);
    }

    @Test
    void submission_isRefusedForARecordInAnotherStatus() {
        assertThatThrownBy(() -> IssuanceTransition.SUBMITTED.after(FLOW, SampleRecordStatus.SENT, RECORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("запись " + RECORD + " в статусе SENT, а не READY");
    }

    @Test
    void error_givesTheErrorStatusOfTheRecord() {
        assertThat(IssuanceTransition.ERROR.after(FLOW, SampleRecordStatus.SENT, RECORD))
                .isEqualTo(SampleRecordStatus.FAILED);
    }

    @Test
    void cancellation_givesTheCancelledStatusOfTheRecord() {
        assertThat(IssuanceTransition.CANCELLED.after(FLOW, SampleRecordStatus.SENT, RECORD))
                .isEqualTo(SampleRecordStatus.STOPPED);
    }
}
