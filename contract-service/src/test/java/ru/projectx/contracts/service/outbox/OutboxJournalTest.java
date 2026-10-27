package ru.projectx.contracts.service.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.MessageEnvelope;
import ru.projectx.contracts.entity.OutboxDraft;
import ru.projectx.contracts.entity.OutboxMessage;
import ru.projectx.contracts.entity.OutboxStatus;
import ru.projectx.contracts.repository.OutboxMessageRepository;

class OutboxJournalTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final MessageEnvelope ENVELOPE = new MessageEnvelope(
            UUID.fromString("7d2a41c9-9f01-4f77-8a1c-2b9cf0e5a442"), REQUEST, "rvl.contract-status.changed",
            "ContractStatusChanged", 1);

    private final OutboxMessageRepository messages = mock();
    private final OutboxJournal journal = new OutboxJournal(messages);

    @Test
    void record_savesAPendingMessageComposedFromTheDraft() {
        given(messages.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        OutboxMessage recorded = journal.record(new OutboxDraft("INDIVIDUAL_REQUEST", REQUEST, ENVELOPE, "{}"), NOW);

        assertThat(recorded.status()).isEqualTo(OutboxStatus.NEW);
        assertThat(recorded.envelope()).isEqualTo(ENVELOPE);
        assertThat(recorded.payload()).contains("{}");
        assertThat(recorded).hasFieldOrPropertyWithValue("createdAt", NOW);
    }
}
