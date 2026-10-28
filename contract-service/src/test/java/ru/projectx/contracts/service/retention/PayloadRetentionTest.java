package ru.projectx.contracts.service.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.config.journal.InboxProperties;
import ru.projectx.contracts.config.journal.OutboxProperties;
import ru.projectx.contracts.repository.InboxMessageRepository;
import ru.projectx.contracts.repository.OutboxMessageRepository;
import ru.projectx.contracts.repository.QuarantineRepository;
import ru.projectx.contracts.support.TestLeadership;

class PayloadRetentionTest {

    private static final Instant NOW = Instant.parse("2026-03-12T07:05:00Z");
    private static final Instant CUTOFF = Instant.parse("2026-02-10T07:05:00Z");

    private final PlatformTransactionManager transactions = mock();
    private final TransactionTemplate transaction = new TransactionTemplate(transactions);
    private final SimpleTransactionStatus status = new SimpleTransactionStatus();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void inboxRetention_erasesBodiesReceivedBeforeTheCutoffInBatchesUntilAShortOne() {
        InboxMessageRepository messages = mock();
        given(transactions.getTransaction(any())).willReturn(status);
        given(messages.erasePayloadsReceivedBefore(CUTOFF, 2)).willReturn(2, 2, 1);
        InboxPayloadRetention retention = new InboxPayloadRetention(messages, inboxProperties(2), transaction, clock,
                TestLeadership.leader());

        assertThat(retention.eraseExpired()).isEqualTo(5);

        verify(messages, times(3)).erasePayloadsReceivedBefore(CUTOFF, 2);
        verify(transactions, times(3)).commit(status);
    }

    @Test
    void outboxRetention_erasesFinishedBodiesCreatedBeforeTheCutoff() {
        OutboxMessageRepository messages = mock();
        given(transactions.getTransaction(any())).willReturn(status);
        given(messages.eraseFinishedPayloadsCreatedBefore(CUTOFF, 500)).willReturn(3);
        OutboxPayloadRetention retention = new OutboxPayloadRetention(messages, outboxProperties(500), transaction, clock,
                TestLeadership.leader());

        assertThat(retention.eraseExpired()).isEqualTo(3);

        verify(messages).eraseFinishedPayloadsCreatedBefore(CUTOFF, 500);
        verify(transactions).commit(status);
    }

    @Test
    void nothingExpired_runsOneEmptyBatch() {
        InboxMessageRepository messages = mock();
        given(transactions.getTransaction(any())).willReturn(status);
        given(messages.erasePayloadsReceivedBefore(CUTOFF, 2)).willReturn(0);

        assertThat(new InboxPayloadRetention(messages, inboxProperties(2), transaction, clock, TestLeadership.leader())
                .eraseExpired()).isZero();

        verify(messages).erasePayloadsReceivedBefore(CUTOFF, 2);
    }

    @Test
    void quarantineRetention_erasesQuarantineBodiesReceivedBeforeTheCutoff() {
        QuarantineRepository quarantine = mock();
        given(transactions.getTransaction(any())).willReturn(status);
        given(quarantine.eraseBodiesReceivedBefore(CUTOFF, 2)).willReturn(2, 0);
        QuarantinePayloadRetention retention = new QuarantinePayloadRetention(quarantine, inboxProperties(2),
                transaction, clock, TestLeadership.leader());

        assertThat(retention.eraseExpired()).isEqualTo(2);

        verify(quarantine, times(2)).eraseBodiesReceivedBefore(CUTOFF, 2);
    }

    @Test
    void standby_erasesNothingAndOpensNoTransaction() {
        InboxMessageRepository messages = mock();

        assertThat(new InboxPayloadRetention(messages, inboxProperties(2), transaction, clock, TestLeadership.standby())
                .eraseExpired()).isZero();

        verifyNoInteractions(messages, transactions);
    }

    private static InboxProperties inboxProperties(int batch) {
        return new InboxProperties(new InboxProperties.Processing(Duration.ofSeconds(10), 100),
                new InboxProperties.Retry(List.of(Duration.ofMinutes(1))),
                new InboxProperties.Retention(Duration.ofDays(30), Duration.ofHours(1), batch),
                new InboxProperties.Pause(Duration.ofSeconds(10), Duration.ofSeconds(30)), Duration.ofSeconds(5));
    }

    private static OutboxProperties outboxProperties(int batch) {
        return new OutboxProperties(new OutboxProperties.Publishing(Duration.ofSeconds(5), 100), Duration.ofSeconds(10),
                new OutboxProperties.Retry(List.of(Duration.ofMinutes(1))),
                new OutboxProperties.Retention(Duration.ofDays(30), Duration.ofHours(1), batch));
    }
}
