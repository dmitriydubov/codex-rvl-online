package ru.projectx.contracts.service.outbox;

import java.time.Instant;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.entity.outbox.OutboxDraft;
import ru.projectx.contracts.entity.outbox.OutboxMessage;
import ru.projectx.contracts.repository.journal.OutboxMessageRepository;

@Service
public class OutboxJournal {

    private final OutboxMessageRepository messages;

    public OutboxJournal(OutboxMessageRepository messages) {
        this.messages = messages;
    }

    public OutboxMessage record(OutboxDraft draft, Instant now) {
        return messages.save(OutboxMessage.compose(draft, now));
    }
}
