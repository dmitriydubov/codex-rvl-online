package ru.projectx.contracts.service.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.entity.MessageAttachment;
import ru.projectx.contracts.entity.StoredFile;
import ru.projectx.contracts.entity.OutboxDraft;
import ru.projectx.contracts.entity.MessageEnvelope;
import ru.projectx.contracts.entity.OutboxMessage;
import ru.projectx.contracts.entity.OutboxStatus;
import ru.projectx.contracts.repository.MessageAttachmentRepository;
import ru.projectx.contracts.repository.OutboxMessageRepository;

class OutboxJournalTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final MessageEnvelope ENVELOPE = new MessageEnvelope(
            UUID.fromString("7d2a41c9-9f01-4f77-8a1c-2b9cf0e5a442"), REQUEST, "rvl.contract-status.changed",
            "ContractStatusChanged", 1);

    private final OutboxMessageRepository messages = mock();
    private final MessageAttachmentRepository attachments = mock();
    private final OutboxJournal journal = new OutboxJournal(messages, attachments);

    @Test
    void record_savesAPendingMessageComposedFromTheDraft() {
        given(messages.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        OutboxMessage recorded = journal.record(new OutboxDraft("INDIVIDUAL_REQUEST", REQUEST, ENVELOPE, "{}"), NOW);

        assertThat(recorded.status()).isEqualTo(OutboxStatus.NEW);
        assertThat(recorded.envelope()).isEqualTo(ENVELOPE);
        assertThat(recorded.payload()).contains("{}");
        assertThat(recorded).hasFieldOrPropertyWithValue("createdAt", NOW);
        verify(attachments).saveAll(List.of());
    }

    @Test
    void record_withAttachments_savesAnAttachmentRowPerFilePointingAtTheSavedMessage() {
        UUID messageId = UUID.fromString("0196c0f1-7a2b-7c3d-8e4f-5a6b7c8d9e0f");
        UUID document = UUID.fromString("3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21");
        StoredFile file = new StoredFile("certificates/0b6c/3c2f.jpg", "0".repeat(64), 76L, "image/jpeg");
        given(messages.save(any())).willAnswer(invocation -> {
            OutboxMessage saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", messageId);
            return saved;
        });

        journal.record(new OutboxDraft("CONTRACT", REQUEST, ENVELOPE, "{}"),
                List.of(new OutboundAttachment(document, file)), NOW);

        ArgumentCaptor<List<MessageAttachment>> saved = ArgumentCaptor.captor();
        verify(attachments).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement()
                .hasFieldOrPropertyWithValue("outboxMessageId", messageId)
                .hasFieldOrPropertyWithValue("documentId", document)
                .hasFieldOrPropertyWithValue("fileKey", file.key())
                .hasFieldOrPropertyWithValue("fileSize", 76L)
                .hasFieldOrPropertyWithValue("createdAt", NOW);
    }
}
