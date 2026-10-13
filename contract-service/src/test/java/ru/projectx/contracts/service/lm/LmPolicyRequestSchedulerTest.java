package ru.projectx.contracts.service.lm;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class LmPolicyRequestSchedulerTest {

    private final LmPolicyRequests requests = mock();
    private final LmPolicyRequestScheduler scheduler = new LmPolicyRequestScheduler(requests);

    @Test
    void prepareDue_runsThePass() {
        scheduler.prepareDue();

        verify(requests).prepareDue();
    }

    @Test
    void failureOfThePass_isLoggedAndSwallowedAtTheBoundary() {
        given(requests.prepareDue()).willThrow(new RuntimeException(new SQLException("база", "08006")));

        assertThatCode(scheduler::prepareDue).doesNotThrowAnyException();
    }
}
