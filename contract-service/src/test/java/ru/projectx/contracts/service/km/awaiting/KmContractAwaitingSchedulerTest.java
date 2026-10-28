package ru.projectx.contracts.service.km.awaiting;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class KmContractAwaitingSchedulerTest {

    private final KmContractAwaiting awaiting = mock();
    private final KmContractAwaitingScheduler scheduler = new KmContractAwaitingScheduler(awaiting);

    @Test
    void refresh_runsThePass() {
        scheduler.refresh();

        verify(awaiting).refresh();
    }

    @Test
    void failureOfThePass_isLoggedAndSwallowedAtTheBoundary() {
        willThrow(new RuntimeException(new SQLException("база", "08006"))).given(awaiting).refresh();

        assertThatCode(scheduler::refresh).doesNotThrowAnyException();
    }
}
