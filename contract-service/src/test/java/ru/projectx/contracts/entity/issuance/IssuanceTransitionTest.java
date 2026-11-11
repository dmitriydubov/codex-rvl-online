package ru.projectx.contracts.entity.issuance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IssuanceTransitionTest {

    private static final UUID RECORD = UUID.fromString("0b7a6a3e-5d1c-4f6e-9a2b-3c4d5e6f7a8b");
    private static final IssuanceFlow<String> FLOW =
            new IssuanceFlow<>("готова", "отправлена", "ошибка", "прекращена", "запись");

    @Test
    void submission_movesARecordFromTheStatusItIsSubmittedFromIntoSubmitted() {
        assertThat(IssuanceTransition.SUBMITTED.after(FLOW, "готова", RECORD)).isEqualTo("отправлена");
    }

    @Test
    void submission_isRefusedForARecordInAnotherStatus() {
        assertThatThrownBy(() -> IssuanceTransition.SUBMITTED.after(FLOW, "отправлена", RECORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("запись " + RECORD + " в статусе отправлена, а не готова");
    }

    @Test
    void error_givesTheErrorStatusOfTheRecord() {
        assertThat(IssuanceTransition.ERROR.after(FLOW, "отправлена", RECORD)).isEqualTo("ошибка");
    }

    @Test
    void cancellation_givesTheCancelledStatusOfTheRecord() {
        assertThat(IssuanceTransition.CANCELLED.after(FLOW, "отправлена", RECORD)).isEqualTo("прекращена");
    }
}
