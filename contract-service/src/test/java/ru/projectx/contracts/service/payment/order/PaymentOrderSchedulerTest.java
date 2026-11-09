package ru.projectx.contracts.service.payment.order;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class PaymentOrderSchedulerTest {

    private final PaymentOrderCreation creation = mock();
    private final PaymentOrderAwaiting awaiting = mock();
    private final PaymentOrderScheduler scheduler = new PaymentOrderScheduler(creation, awaiting);

    @Test
    void createDue_runsTheCreationPass() {
        scheduler.createDue();

        verify(creation).createDue();
    }

    @Test
    void refreshAwaiting_runsTheSummary() {
        scheduler.refreshAwaiting();

        verify(awaiting).refresh();
    }

    @Test
    void failuresOfThePasses_areSwallowedAtTheBoundary() {
        given(creation.createDue()).willThrow(new IllegalStateException("стенд"));
        willThrow(new IllegalStateException("стенд")).given(awaiting).refresh();

        assertThatCode(scheduler::createDue).doesNotThrowAnyException();
        assertThatCode(scheduler::refreshAwaiting).doesNotThrowAnyException();
    }
}
