package ru.projectx.contracts.service.inbox;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.inbox.InboxMessage;
import ru.projectx.contracts.entity.inbox.InboxReceipt;
import ru.projectx.contracts.entity.inbox.InboxStatus;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.repository.journal.InboxMessageRepository;
import ru.projectx.contracts.service.InboxLogContext;
import ru.projectx.contracts.service.leadership.LeadershipFence;

@Service
public class InboxMessageHandling {

    static final String PAYLOAD_ERASED = "тело сообщения стёрто по сроку хранения";

    private final InboxMessageRepository messages;
    private final InboxEventHandlers handlers;
    private final TransactionTemplate transaction;
    private final LeadershipFence fence;
    private final Clock clock;

    public InboxMessageHandling(InboxMessageRepository messages, InboxEventHandlers handlers,
            TransactionTemplate transaction, LeadershipFence fence, Clock clock) {
        this.messages = messages;
        this.handlers = handlers;
        this.transaction = transaction;
        this.fence = fence;
        this.clock = clock;
    }

    record PendingMessage(InboxReceipt receipt, String payload) {
    }

    public void process(UUID id) {
        Optional<PendingMessage> pending = transaction.execute(status -> begin(id));
        if (pending.isEmpty()) {
            return;
        }
        InboxReceipt receipt = pending.get().receipt();
        InboxLogContext.run(receipt, () -> {
            InboxWork work = handlers.forEventType(receipt.eventType()).prepare(pending.get().payload());
            transaction.executeWithoutResult(status -> complete(id, work));
        });
    }

    private Optional<PendingMessage> begin(UUID id) {
        InboxMessage message = load(id);
        if (message.status() != InboxStatus.NEW) {
            return Optional.empty();
        }
        InboxReceipt receipt = message.receipt();
        Optional<Integer> processed = processedVersionNotBelow(receipt);
        fence.holdUntilCommit();
        if (processed.isPresent()) {
            message.markSkipped("версия объекта " + receipt.entityVersion() + " не выше обработанной "
                    + processed.get(), clock.instant());
            return Optional.empty();
        }
        String payload = message.payload().orElseThrow(() -> new ContractViolationException(PAYLOAD_ERASED));
        return Optional.of(new PendingMessage(receipt, payload));
    }

    private Optional<Integer> processedVersionNotBelow(InboxReceipt receipt) {
        if (handlers.forEventType(receipt.eventType()).ordering() != InboxOrdering.BY_REQUEST_VERSION) {
            return Optional.empty();
        }
        return messages.maxProcessedVersion(receipt.correlationId(), handlers.orderedByRequestVersion())
                .filter(version -> version >= receipt.entityVersion());
    }

    private void complete(UUID id, InboxWork work) {
        InboxMessage message = load(id);
        if (message.status() != InboxStatus.NEW) {
            return;
        }
        work.apply(clock.instant());
        fence.holdUntilCommit();
        message.markProcessed(clock.instant());
    }

    private InboxMessage load(UUID id) {
        return messages.findById(id)
                .orElseThrow(() -> new IllegalStateException("в журнале входящих нет записи " + id));
    }
}
