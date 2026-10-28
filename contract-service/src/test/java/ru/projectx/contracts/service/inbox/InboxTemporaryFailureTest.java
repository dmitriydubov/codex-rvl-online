package ru.projectx.contracts.service.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.sql.SQLTransientConnectionException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.CannotCreateTransactionException;
import ru.projectx.contracts.config.journal.InboxProperties;
import ru.projectx.contracts.entity.inbox.InboxMessage;
import ru.projectx.contracts.entity.inbox.InboxReceipt;
import ru.projectx.contracts.entity.inbox.InboxStatus;
import ru.projectx.contracts.repository.journal.InboxMessageRepository;
import ru.projectx.contracts.service.leadership.LeadershipFence;
import ru.projectx.contracts.support.TestLeadership;

class InboxTemporaryFailureTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final String POOL_TIMEOUT =
            "временный отказ доступа к данным: java.sql.SQLTransientConnectionException";

    private final InboxMessageRepository messages = mock();
    private final InboxMessageHandling handling = mock();
    private final InboxMetrics metrics = mock();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InboxProperties properties = new InboxProperties(
            new InboxProperties.Processing(Duration.ofSeconds(10), 7),
            new InboxProperties.Retry(List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15))),
            new InboxProperties.Retention(Duration.ofDays(30), Duration.ofHours(1), 500),
            new InboxProperties.Pause(Duration.ofSeconds(10), Duration.ofSeconds(30)), Duration.ofSeconds(5));
    private final InboxProcessor processor = new InboxProcessor(messages, handling,
            new InboxOutcomes(messages, properties, metrics, mock(LeadershipFence.class), clock), properties,
            TestLeadership.leader(), clock);
    private final InboxMessage message = InboxMessage.receive(new InboxReceipt(
            UUID.fromString("b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11"),
            UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803"),
            "rvl.contract-task.upserted", "ContractTaskUpserted", 1), "{}", NOW);

    @BeforeEach
    void poolTimesOutWithoutSqlState() {
        given(messages.findDue(NOW, Limit.of(7))).willReturn(List.of(message));
        given(messages.findById(message.id())).willReturn(Optional.of(message));
        willThrow(new CannotCreateTransactionException("нет соединения",
                new SQLTransientConnectionException("пул исчерпан"))).given(handling).process(message.id());
    }

    @Test
    void poolTimeoutWithoutSqlState_putsTheMessageOnTheRetryLadder() {
        processor.processDue();

        assertThat(message.status()).isEqualTo(InboxStatus.NEW);
        assertThat(message.attempts()).isEqualTo(1);
        assertThat(message.errorMessage()).contains(POOL_TIMEOUT);
        assertThat(message).hasFieldOrPropertyWithValue("nextAttemptAt", NOW.plus(Duration.ofMinutes(1)));
        verify(metrics, never()).countFailed(anyString());
    }

    @Test
    void poolTimeoutAfterTheLadder_rejectsTheMessage() {
        message.markRetry("отказ", NOW);
        message.markRetry("отказ", NOW);
        message.markRetry("отказ", NOW);

        processor.processDue();

        assertThat(message.status()).isEqualTo(InboxStatus.FAILED);
        assertThat(message.errorMessage()).contains("повторы исчерпаны на попытке 4: " + POOL_TIMEOUT);
        verify(metrics).countFailed("ContractTaskUpserted");
    }
}
