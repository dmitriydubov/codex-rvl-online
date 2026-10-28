package ru.projectx.contracts.service.inbox.receipt;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.inbox.InboxMessage;
import ru.projectx.contracts.entity.inbox.InboxReceipt;
import ru.projectx.contracts.repository.journal.InboxMessageRepository;
import ru.projectx.contracts.service.leadership.LeadershipFence;

@Service
public class InboxReceiver {

    private final InboxMessageRepository messages;
    private final LeadershipFence fence;
    private final Clock clock;

    public InboxReceiver(InboxMessageRepository messages, LeadershipFence fence, Clock clock) {
        this.messages = messages;
        this.fence = fence;
        this.clock = clock;
    }

    @Transactional
    public ReceiptOutcome receive(InboxReceipt receipt, String payload) {
        boolean duplicate = messages.existsByMessageId(receipt.messageId());
        fence.holdUntilCommit();
        if (duplicate) {
            return ReceiptOutcome.DUPLICATE;
        }
        messages.save(InboxMessage.receive(receipt, payload, clock.instant()));
        return ReceiptOutcome.STORED;
    }
}
