package ru.projectx.contracts.service.inbox;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class InboxSchedulerTest {

    private final InboxProcessor processor = mock();
    private final InboxPayloadErasure erasure = mock();
    private final InboxScheduler scheduler = new InboxScheduler(processor, erasure);

    @Test
    void processDue_runsTheProcessor() {
        scheduler.processDue();

        verify(processor).processDue();
    }

    @Test
    void erasePayloads_runsTheErasure() {
        scheduler.erasePayloads();

        verify(erasure).eraseExpired();
    }

    @Test
    void failuresOfEitherPass_areLoggedAndSwallowedAtTheBoundary() {
        given(processor.processDue()).willThrow(new RuntimeException(new SQLException("база", "08006")));
        given(erasure.eraseExpired()).willThrow(new IllegalStateException("база"));

        assertThatCode(scheduler::processDue).doesNotThrowAnyException();
        assertThatCode(scheduler::erasePayloads).doesNotThrowAnyException();
    }
}
