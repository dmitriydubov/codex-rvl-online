package ru.projectx.contracts.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.inbox.InboxMessage;
import ru.projectx.contracts.entity.inbox.InboxReceipt;
import ru.projectx.contracts.entity.inbox.InboxStatus;
import ru.projectx.contracts.support.IntegrationTest;

@IntegrationTest
class InboxMessageRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");

    @Autowired
    private InboxMessageRepository messages;

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
        InboxMessage stored = transaction.execute(status -> messages.save(message("b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11", 1, NOW)));

        assertThat(messages.existsByMessageId(UUID.fromString("b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11"))).isTrue();
        assertThat(messages.findById(stored.id())).get().satisfies(found -> {
            assertThat(found.status()).isEqualTo(InboxStatus.NEW);
            assertThat(found.payload()).contains("{\"data\": {\"n\": 1}}");
        });
        assertThat(jdbc.sql("select status::text from inbox_messages").query(String.class).single()).isEqualTo("NEW");
    }

    @Test
    void due_areNewMessagesWhoseNextAttemptHasComeInOrderOfReceiptWithinTheLimit() {
        transaction.executeWithoutResult(status -> {
            InboxMessage later = messages.save(message("0c7d2e4f-6a8b-4c0d-9e1f-2a3b4c5d6e7f", 1, NOW.plusSeconds(1)));
            InboxMessage first = messages.save(message("b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11", 1, NOW));
            InboxMessage retrying = messages.save(message("c3f80a67-9d12-4b58-8e44-0a7b1f25e903", 1, NOW.minusSeconds(5)));
            retrying.markRetry("отказ", NOW.plusSeconds(60));
            InboxMessage retryDue = messages.save(message("d4a91b78-0e23-4c69-9f55-1b8c2a36f014", 1, NOW.minusSeconds(9)));
            retryDue.markRetry("отказ", NOW);
            InboxMessage processed = messages.save(message("5b1e7d44-2c09-4a63-8f17-6e0b9a3c1d05", 1, NOW.minusSeconds(10)));
            processed.markProcessed(NOW);
            assertThat(List.of(later, first)).hasSize(2);
        });

        List<UUID> due = messages.findDue(NOW, Limit.of(10)).stream().map(m -> m.receipt().messageId()).toList();
        List<UUID> limited = messages.findDue(NOW, Limit.of(1)).stream().map(m -> m.receipt().messageId()).toList();

        assertThat(due).containsExactly(UUID.fromString("d4a91b78-0e23-4c69-9f55-1b8c2a36f014"),
                UUID.fromString("b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11"),
                UUID.fromString("0c7d2e4f-6a8b-4c0d-9e1f-2a3b4c5d6e7f"));
        assertThat(limited).containsExactly(UUID.fromString("d4a91b78-0e23-4c69-9f55-1b8c2a36f014"));
    }

    @Test
    void processedVersion_isTheHighestAmongProcessedMessagesOfTheRequestAndNamedEventTypesOnly() {
        transaction.executeWithoutResult(status -> {
            messages.save(message("b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11", 2, NOW)).markProcessed(NOW);
            messages.save(message("0c7d2e4f-6a8b-4c0d-9e1f-2a3b4c5d6e7f", 5, NOW)).markFailed("отказ");
            messages.save(message("c3f80a67-9d12-4b58-8e44-0a7b1f25e903", 4, NOW));
            messages.save(issued("5b1e7d44-2c09-4a63-8f17-6e0b9a3c1d05", 7, NOW)).markProcessed(NOW);
        });
        Set<String> tasks = Set.of("ContractTaskUpserted", "ContractTaskCancelled");

        assertThat(messages.maxProcessedVersion(REQUEST, tasks)).contains(2);
        assertThat(messages.maxProcessedVersion(REQUEST, Set.of("LmPolicyIssued"))).contains(7);
        assertThat(messages.maxProcessedVersion(UUID.fromString("7f2c9e4a-1b3d-4e5f-8a6b-9c0d1e2f3a4b"), tasks)).isEmpty();
    }

    @Test
    void erasure_clearsBodiesReceivedBeforeCutoffInBatchesAndKeepsTheRest() {
        transaction.executeWithoutResult(status -> {
            messages.save(message("b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11", 1, NOW.minusSeconds(3)));
            messages.save(message("0c7d2e4f-6a8b-4c0d-9e1f-2a3b4c5d6e7f", 1, NOW.minusSeconds(2)));
            messages.save(message("c3f80a67-9d12-4b58-8e44-0a7b1f25e903", 1, NOW));
        });

        int firstBatch = transaction.execute(status -> messages.erasePayloadsReceivedBefore(NOW.minusSeconds(1), 1));
        int secondBatch = transaction.execute(status -> messages.erasePayloadsReceivedBefore(NOW.minusSeconds(1), 1));
        int thirdBatch = transaction.execute(status -> messages.erasePayloadsReceivedBefore(NOW.minusSeconds(1), 1));

        assertThat(List.of(firstBatch, secondBatch, thirdBatch)).containsExactly(1, 1, 0);
        assertThat(jdbc.sql("select count(*) from inbox_messages where payload is null").query(Long.class).single())
                .isEqualTo(2);
        assertThat(messages.findAll()).filteredOn(m -> m.payload().isPresent())
                .extracting(m -> m.receipt().messageId())
                .containsExactly(UUID.fromString("c3f80a67-9d12-4b58-8e44-0a7b1f25e903"));
    }

    private static InboxMessage message(String messageId, int version, Instant receivedAt) {
        return InboxMessage.receive(new InboxReceipt(UUID.fromString(messageId), REQUEST, "rvl.contract-task.upserted",
                "ContractTaskUpserted", version), "{\"data\": {\"n\": 1}}", receivedAt);
    }

    private static InboxMessage issued(String messageId, int version, Instant receivedAt) {
        return InboxMessage.receive(new InboxReceipt(UUID.fromString(messageId), REQUEST, "sogaz1c.lm-policy.issued",
                "LmPolicyIssued", version), "{\"data\": {\"n\": 1}}", receivedAt);
    }
}
