package ru.projectx.contracts.service.outbox;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.config.journal.OutboxProperties;
import ru.projectx.contracts.entity.MessageEnvelope;
import ru.projectx.contracts.entity.outbox.OutboxMessage;
import ru.projectx.contracts.repository.journal.OutboxMessageRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;

@Service
public class OutboxPublisher {

    static final String PAYLOAD_ERASED = "тело сообщения стёрто по сроку хранения";

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxMessageRepository messages;
    private final OutboundPublisher publisher;
    private final OutboxOutcomes outcomes;
    private final Limit batch;
    private final BackgroundLeadership leadership;
    private final Clock clock;

    public OutboxPublisher(OutboxMessageRepository messages, OutboundPublisher publisher,
            OutboxOutcomes outcomes, OutboxProperties properties, BackgroundLeadership leadership, Clock clock) {
        this.batch = Limit.of(properties.publishing().batchSize());
        this.messages = messages;
        this.publisher = publisher;
        this.outcomes = outcomes;
        this.leadership = leadership;
        this.clock = clock;
    }

    public int publishDue() {
        return leadership.countAsLeader(() -> publish(messages.findDue(clock.instant(), batch)));
    }

    private int publish(List<OutboxMessage> due) {
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
        Optional<String> payload = message.payload();
        if (payload.isEmpty()) {
            return new PublishOutcome.Refused(PAYLOAD_ERASED);
        }
        return publisher.publish(outbound(message.envelope(), payload.get()));
    }

    private static OutboundMessage outbound(MessageEnvelope envelope, String payload) {
        return new OutboundMessage(envelope.messageId(), envelope.correlationId(), envelope.eventType(), payload);
    }

    private int record(OutboxMessage message, PublishOutcome outcome) {
        if (outcome instanceof PublishOutcome.Attempted attempted) {
            outcomes.record(message.id(), attempted);
            return 1;
        }
        return 0;
    }
}
