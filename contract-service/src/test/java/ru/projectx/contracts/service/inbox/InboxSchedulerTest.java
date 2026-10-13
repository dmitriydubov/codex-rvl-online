package ru.projectx.contracts.service.inbox;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class InboxSchedulerTest {

    private final InboxProcessor processor = mock();
    private final InboxScheduler scheduler = new InboxScheduler(processor);

    @Test
    void processDue_runsTheProcessor() {
        scheduler.processDue();

        verify(processor).processDue();
    }

    @Test
    void failureOfThePass_isLoggedAndSwallowedAtTheBoundary() {
        given(processor.processDue()).willThrow(new RuntimeException(new SQLException("база", "08006")));

        assertThatCode(scheduler::processDue).doesNotThrowAnyException();
    }
}
