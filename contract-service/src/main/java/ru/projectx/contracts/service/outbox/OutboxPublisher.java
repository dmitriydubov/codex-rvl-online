package ru.projectx.contracts.service.outbox;

import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.config.OutboxProperties;
import ru.projectx.contracts.entity.MessageEnvelope;
import ru.projectx.contracts.entity.OutboxMessage;
import ru.projectx.contracts.repository.OutboxMessageRepository;

@Service
public class OutboxPublisher {

    static final String PAYLOAD_ERASED = "тело сообщения стёрто по сроку хранения";

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxMessageRepository messages;
    private final OutboundPublisher publisher;
    private final OutboxOutcomes outcomes;
    private final Limit batch;
    private final Clock clock;

    public OutboxPublisher(OutboxMessageRepository messages, OutboundPublisher publisher, OutboxOutcomes outcomes,
            OutboxProperties properties, Clock clock) {
        this.batch = Limit.of(properties.publishing().batchSize());
        this.messages = messages;
        this.publisher = publisher;
        this.outcomes = outcomes;
        this.clock = clock;
    }

    public int publishDue() {
        List<OutboxMessage> due = messages.findDue(clock.instant(), batch);
        int handled = 0;
        for (OutboxMessage message : due) {
            PublishOutcome outcome = attempt(message);
            handled += record(message, outcome);
            if (outcome.endsPass()) {
                log.warn("проход публикации остановлен на сообщении {}: {}", message.envelope().messageId(),
                        outcome.reason());
                break;
            }
        }
        return handled;
    }

    private PublishOutcome attempt(OutboxMessage message) {
        MessageEnvelope envelope = message.envelope();
        return message.payload()
                .map(payload -> publisher.publish(new OutboundMessage(envelope.messageId(), envelope.correlationId(),
                        envelope.eventType(), payload)))
                .orElseGet(() -> new PublishOutcome.Refused(PAYLOAD_ERASED));
    }

    private int record(OutboxMessage message, PublishOutcome outcome) {
        if (!outcome.attempted()) {
            return 0;
        }
        outcomes.record(message.id(), outcome);
        return 1;
    }
}
