package ru.projectx.contracts.service.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.MessageEnvelope;
import ru.projectx.contracts.entity.OutboxMessage;

@Component
public class OutboxRejection {

    private static final Logger log = LoggerFactory.getLogger(OutboxRejection.class);

    private final OutboxMetrics metrics;
    private final Map<String, RejectedOutboxMessages> reactions;

    public OutboxRejection(OutboxMetrics metrics, List<RejectedOutboxMessages> reactions) {
        this.metrics = metrics;
        this.reactions = reactions.stream()
                .collect(Collectors.toUnmodifiableMap(RejectedOutboxMessages::aggregateType, Function.identity()));
    }

    public void reject(OutboxMessage message, String reason, Instant now) {
        message.markFailed(reason);
        MessageEnvelope envelope = message.envelope();
        log.error("исходящее сообщение {} отклонено окончательно: тип события {}, попытка {}, причина: {}",
                envelope.messageId(), envelope.eventType(), message.attempts(), reason);
        metrics.countFailed(envelope.eventType());
        Optional.ofNullable(reactions.get(message.aggregateType()))
                .ifPresent(reaction -> reaction.rejected(message.aggregateId(), now));
    }
}
