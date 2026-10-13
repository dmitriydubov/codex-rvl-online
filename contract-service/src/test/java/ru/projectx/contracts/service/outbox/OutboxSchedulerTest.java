package ru.projectx.contracts.service.outbox;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class OutboxSchedulerTest {

    private final OutboxPublisher publisher = mock();
    private final OutboxScheduler scheduler = new OutboxScheduler(publisher);

    @Test
    void publishDue_runsThePublisher() {
        scheduler.publishDue();

        verify(publisher).publishDue();
    }

    @Test
    void failureOfThePass_isLoggedAndSwallowedAtTheBoundary() {
        given(publisher.publishDue()).willThrow(new IllegalStateException("брокер"));

        assertThatCode(scheduler::publishDue).doesNotThrowAnyException();
    }
}
