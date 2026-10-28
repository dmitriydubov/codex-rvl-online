package ru.projectx.contracts.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.outbox.OutboxDraft;
import ru.projectx.contracts.entity.MessageEnvelope;
import ru.projectx.contracts.entity.outbox.OutboxMessage;
import ru.projectx.contracts.entity.outbox.OutboxStatus;
import ru.projectx.contracts.support.IntegrationTest;

@IntegrationTest
class OutboxMessageRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final String FIRST = "7d2a41c9-9f01-4f77-8a1c-2b9cf0e5a442";
    private static final String SECOND = "0c7d2e4f-6a8b-4c0d-9e1f-2a3b4c5d6e7f";
    private static final String THIRD = "c3f80a67-9d12-4b58-8e44-0a7b1f25e903";
    private static final String FOURTH = "d4a91b78-0e23-4c69-9f55-1b8c2a36f014";
    private static final String FIFTH = "5b1e7d44-2c09-4a63-8f17-6e0b9a3c1d05";

    @Autowired
    private OutboxMessageRepository messages;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyJournal() {
        messages.deleteAll();
    }

    @Test
    void message_isStoredWithNativeStatusAndJsonBody() {
        OutboxMessage stored = transaction.execute(status -> messages.save(message(FIRST, NOW)));

        assertThat(messages.findById(stored.id())).get().satisfies(found -> {
            assertThat(found.status()).isEqualTo(OutboxStatus.NEW);
            assertThat(found.payload()).contains("{\"data\": {\"n\": 1}}");
            assertThat(found.envelope().messageId()).isEqualTo(UUID.fromString(FIRST));
        });
        assertThat(jdbc.sql("select status::text from outbox_messages").query(String.class).single()).isEqualTo("NEW");
    }

    @Test
    void due_areNewMessagesWhoseNextAttemptHasComeInOrderOfCreationWithinTheLimit() {
        transaction.executeWithoutResult(status -> {
            messages.save(message(SECOND, NOW.plusSeconds(1)));
            messages.save(message(FIRST, NOW));
            messages.save(message(THIRD, NOW.minusSeconds(5))).markRetry("отказ", NOW.plusSeconds(60));
            messages.save(message(FOURTH, NOW.minusSeconds(9))).markRetry("отказ", NOW);
            messages.save(message(FIFTH, NOW.minusSeconds(10))).markPublished(NOW);
        });

        List<UUID> due = messages.findDue(NOW, Limit.of(10)).stream().map(m -> m.envelope().messageId()).toList();
        List<UUID> limited = messages.findDue(NOW, Limit.of(1)).stream().map(m -> m.envelope().messageId()).toList();

        assertThat(due).containsExactly(UUID.fromString(FOURTH), UUID.fromString(FIRST), UUID.fromString(SECOND));
        assertThat(limited).containsExactly(UUID.fromString(FOURTH));
    }

    @Test
    void erasure_clearsFinishedBodiesCreatedBeforeCutoffInBatchesAndKeepsPendingAndRecentOnes() {
        transaction.executeWithoutResult(status -> {
            messages.save(message(FIRST, NOW.minusSeconds(3))).markPublished(NOW);
            messages.save(message(SECOND, NOW.minusSeconds(2))).markFailed("отказ");
            messages.save(message(THIRD, NOW.minusSeconds(2)));
            messages.save(message(FOURTH, NOW)).markPublished(NOW);
        });

        int firstBatch = transaction.execute(status -> messages.eraseFinishedPayloadsCreatedBefore(NOW.minusSeconds(1), 1));
        int secondBatch = transaction.execute(status -> messages.eraseFinishedPayloadsCreatedBefore(NOW.minusSeconds(1), 1));
        int thirdBatch = transaction.execute(status -> messages.eraseFinishedPayloadsCreatedBefore(NOW.minusSeconds(1), 1));

        assertThat(List.of(firstBatch, secondBatch, thirdBatch)).containsExactly(1, 1, 0);
        assertThat(messages.findAll()).filteredOn(m -> m.payload().isPresent())
                .extracting(m -> m.envelope().messageId())
                .containsExactlyInAnyOrder(UUID.fromString(THIRD), UUID.fromString(FOURTH));
    }

    private static OutboxMessage message(String messageId, Instant createdAt) {
        return OutboxMessage.compose(new OutboxDraft("INDIVIDUAL_REQUEST", REQUEST,
                new MessageEnvelope(UUID.fromString(messageId), REQUEST, "rvl.contract-status.changed",
                        "ContractStatusChanged", 1), "{\"data\": {\"n\": 1}}"), createdAt);
    }
}
