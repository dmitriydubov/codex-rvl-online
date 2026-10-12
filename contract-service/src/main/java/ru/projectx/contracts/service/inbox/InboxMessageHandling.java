package ru.projectx.contracts.service.inbox;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.InboxMessage;
import ru.projectx.contracts.entity.InboxReceipt;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.repository.InboxMessageRepository;
import ru.projectx.contracts.service.InboxLogContext;

@Service
public class InboxMessageHandling {

    static final String PAYLOAD_ERASED = "тело сообщения стёрто по сроку хранения";

    private final InboxMessageRepository messages;
    private final InboxEventHandlers handlers;
    private final Clock clock;

    public InboxMessageHandling(InboxMessageRepository messages, InboxEventHandlers handlers, Clock clock) {
        this.messages = messages;
        this.handlers = handlers;
        this.clock = clock;
    }

    @Transactional
    public void process(UUID id) {
        InboxMessage message = messages.findById(id)
                .orElseThrow(() -> new IllegalStateException("в журнале входящих нет записи " + id));
        InboxLogContext.run(message.receipt(), () -> handle(message, clock.instant()));
    }

    private void handle(InboxMessage message, Instant now) {
        InboxReceipt receipt = message.receipt();
        Optional<Integer> processed = messages.maxProcessedVersion(receipt.correlationId())
                .filter(version -> version >= receipt.entityVersion());
        if (processed.isPresent()) {
            message.markSkipped("версия объекта " + receipt.entityVersion() + " не выше обработанной "
                    + processed.get(), now);
            return;
        }
        String payload = message.payload().orElseThrow(() -> new ContractViolationException(PAYLOAD_ERASED));
        handlers.forEventType(receipt.eventType()).handle(payload, now);
        message.markProcessed(now);
    }
}
