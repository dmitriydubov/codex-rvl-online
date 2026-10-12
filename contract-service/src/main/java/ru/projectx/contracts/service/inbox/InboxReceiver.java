package ru.projectx.contracts.service.inbox;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.InboxMessage;
import ru.projectx.contracts.entity.InboxReceipt;
import ru.projectx.contracts.repository.InboxMessageRepository;

@Service
public class InboxReceiver {

    private final InboxMessageRepository messages;
    private final Clock clock;

    public InboxReceiver(InboxMessageRepository messages, Clock clock) {
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional
    public ReceiptOutcome receive(InboxReceipt receipt, String payload) {
        if (messages.existsByMessageId(receipt.messageId())) {
            return ReceiptOutcome.DUPLICATE;
        }
        messages.save(InboxMessage.receive(receipt, payload, clock.instant()));
        return ReceiptOutcome.STORED;
    }
}
