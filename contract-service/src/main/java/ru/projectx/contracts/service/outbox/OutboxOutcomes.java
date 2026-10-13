package ru.projectx.contracts.service.outbox;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.config.OutboxProperties;
import ru.projectx.contracts.entity.MessageEnvelope;
import ru.projectx.contracts.entity.OutboxMessage;
import ru.projectx.contracts.entity.OutboxStatus;
import ru.projectx.contracts.repository.OutboxMessageRepository;

@Service
public class OutboxOutcomes {

    private static final Logger log = LoggerFactory.getLogger(OutboxOutcomes.class);

    private final OutboxMessageRepository messages;
    private final OutboxProperties.Retry retry;
    private final OutboxMetrics metrics;
    private final Clock clock;

    public OutboxOutcomes(OutboxMessageRepository messages, OutboxProperties properties, OutboxMetrics metrics,
            Clock clock) {
        this.retry = properties.retry();
        this.messages = messages;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public void record(UUID id, PublishOutcome.Attempted outcome) {
        OutboxMessage message = messages.findById(id)
                .orElseThrow(() -> new IllegalStateException("в журнале исходящих нет записи " + id));
        if (message.status() != OutboxStatus.NEW) {
            log.warn("исход исходящего сообщения {} уже зафиксирован: {}", message.envelope().messageId(),
                    message.status());
            return;
        }
        switch (outcome) {
            case PublishOutcome.Delivered delivered -> message.markPublished(clock.instant());
            case PublishOutcome.Refused refused -> reject(message, refused.reason());
            case PublishOutcome.Rejected rejected -> retryOrReject(message, rejected.reason());
            case PublishOutcome.Unconfirmed unconfirmed -> retryOrReject(message, unconfirmed.reason());
        }
    }

    private void retryOrReject(OutboxMessage message, String reason) {
        int attempt = message.attempts() + 1;
        Optional<Duration> delay = retry.delayAfterFailedAttempt(attempt);
        if (delay.isEmpty()) {
            reject(message, "повторы исчерпаны на попытке " + attempt + ": " + reason);
            return;
        }
        message.markRetry(reason, clock.instant().plus(delay.get()));
        log.warn("исходящее сообщение {} отложено до повтора после попытки {}: {}",
                message.envelope().messageId(), attempt, reason);
    }

    private void reject(OutboxMessage message, String reason) {
        message.markFailed(reason);
        MessageEnvelope envelope = message.envelope();
        log.error("исходящее сообщение {} отклонено окончательно: тип события {}, попытка {}, причина: {}",
                envelope.messageId(), envelope.eventType(), message.attempts(), reason);
        metrics.countFailed(envelope.eventType());
    }
}
