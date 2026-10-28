package ru.projectx.contracts.service.retention;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class PayloadRetentionSchedulerTest {

    private final InboxPayloadRetention inbox = mock();
    private final QuarantinePayloadRetention quarantine = mock();
    private final OutboxPayloadRetention outbox = mock();
    private final PayloadRetentionScheduler scheduler = new PayloadRetentionScheduler(inbox, quarantine, outbox);

    @Test
    void eachPass_runsItsOwnRetention() {
        scheduler.eraseInboxPayloads();
        scheduler.eraseOutboxPayloads();

        verify(inbox).eraseExpired();
        verify(quarantine).eraseExpired();
        verify(outbox).eraseExpired();
    }

    @Test
    void failuresOfEitherPass_areLoggedAndSwallowedAtTheBoundary() {
        given(inbox.eraseExpired()).willThrow(new RuntimeException(new SQLException("база", "08006")));
        given(outbox.eraseExpired()).willThrow(new IllegalStateException("база"));

        assertThatCode(scheduler::eraseInboxPayloads).doesNotThrowAnyException();
        assertThatCode(scheduler::eraseOutboxPayloads).doesNotThrowAnyException();
    }
}
