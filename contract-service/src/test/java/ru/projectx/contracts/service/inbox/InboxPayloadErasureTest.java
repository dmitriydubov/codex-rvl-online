package ru.projectx.contracts.service.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import ru.projectx.contracts.config.InboxProperties;
import ru.projectx.contracts.repository.InboxMessageRepository;

class InboxPayloadErasureTest {

    private static final Instant NOW = Instant.parse("2026-03-12T07:05:00Z");
    private static final Instant CUTOFF = Instant.parse("2026-02-10T07:05:00Z");

    private final InboxMessageRepository messages = mock();
    private final PlatformTransactionManager transactions = mock();
    private final InboxPayloadErasure erasure = new InboxPayloadErasure(messages,
            new InboxProperties(new InboxProperties.Processing(Duration.ofSeconds(10), 100),
                    new InboxProperties.Retry(List.of(Duration.ofMinutes(1))),
                    new InboxProperties.Retention(Duration.ofDays(30), Duration.ofHours(1), 2),
                    Duration.ofSeconds(5)),
            transactions, Clock.fixed(NOW, ZoneOffset.UTC));

    private final SimpleTransactionStatus status = new SimpleTransactionStatus();

    @Test
    void erasure_runsBatchesUntilABatchIsNotFull() {
        given(transactions.getTransaction(any())).willReturn(status);
        given(messages.erasePayloadsReceivedBefore(CUTOFF, 2)).willReturn(2, 2, 1);

        assertThat(erasure.eraseExpired()).isEqualTo(5);

        verify(messages, times(3)).erasePayloadsReceivedBefore(CUTOFF, 2);
        verify(transactions, times(3)).commit(status);
    }

    @Test
    void erasure_withNothingExpired_runsOneEmptyBatch() {
        given(transactions.getTransaction(any())).willReturn(status);
        given(messages.erasePayloadsReceivedBefore(CUTOFF, 2)).willReturn(0);

        assertThat(erasure.eraseExpired()).isZero();
        verify(messages).erasePayloadsReceivedBefore(CUTOFF, 2);
    }
}
