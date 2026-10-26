package ru.projectx.contracts.service.outbox;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.entity.MessageAttachment;
import ru.projectx.contracts.entity.OutboxDraft;
import ru.projectx.contracts.entity.OutboxMessage;
import ru.projectx.contracts.repository.MessageAttachmentRepository;
import ru.projectx.contracts.repository.OutboxMessageRepository;

@Service
public class OutboxJournal {

    private final OutboxMessageRepository messages;
    private final MessageAttachmentRepository attachments;

    public OutboxJournal(OutboxMessageRepository messages, MessageAttachmentRepository attachments) {
        this.messages = messages;
        this.attachments = attachments;
    }

    public OutboxMessage record(OutboxDraft draft, Instant now) {
        return record(draft, List.of(), now);
    }

    public OutboxMessage record(OutboxDraft draft, List<OutboundAttachment> files, Instant now) {
        OutboxMessage message = messages.save(OutboxMessage.compose(draft, now));
        attachments.saveAll(files.stream()
                .map(file -> MessageAttachment.of(message.id(), file.documentId(), file.file(), now))
                .toList());
        return message;
    }
}
