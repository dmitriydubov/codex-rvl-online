package ru.projectx.contracts.service.km;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class KmContractSubmissionSchedulerTest {

    private final KmContractSubmissions submissions = mock();
    private final KmContractSubmissionScheduler scheduler = new KmContractSubmissionScheduler(submissions);

    @Test
    void prepareDue_runsThePass() {
        scheduler.prepareDue();

        verify(submissions).prepareDue();
    }

    @Test
    void failureOfThePass_isLoggedAndSwallowedAtTheBoundary() {
        given(submissions.prepareDue()).willThrow(new RuntimeException(new SQLException("база", "08006")));

        assertThatCode(scheduler::prepareDue).doesNotThrowAnyException();
    }
}
