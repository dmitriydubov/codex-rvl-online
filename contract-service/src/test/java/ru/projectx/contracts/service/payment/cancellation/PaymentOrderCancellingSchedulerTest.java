package ru.projectx.contracts.service.payment.cancellation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class PaymentOrderCancellingSchedulerTest {

    private final PaymentOrderCancelling cancelling = mock();
    private final PaymentOrderCancellingScheduler scheduler = new PaymentOrderCancellingScheduler(cancelling);

    @Test
    void cancelDue_runsThePass() {
        scheduler.cancelDue();

        verify(cancelling).cancelDue();
    }

    @Test
    void failureOfThePass_isSwallowedAtTheBoundary() {
        given(cancelling.cancelDue()).willThrow(new IllegalStateException("стенд"));

        assertThatCode(scheduler::cancelDue).doesNotThrowAnyException();
    }
}
