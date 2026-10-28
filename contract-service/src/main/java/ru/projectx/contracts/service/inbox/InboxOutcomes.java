package ru.projectx.contracts.service.inbox;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.config.InboxProperties;
import ru.projectx.contracts.entity.inbox.InboxMessage;
import ru.projectx.contracts.entity.inbox.InboxReceipt;
import ru.projectx.contracts.entity.inbox.InboxStatus;
import ru.projectx.contracts.repository.InboxMessageRepository;
import ru.projectx.contracts.service.InboxLogContext;
import ru.projectx.contracts.service.leadership.LeadershipFence;

@Service
public class InboxOutcomes {

    private static final Logger log = LoggerFactory.getLogger(InboxOutcomes.class);

    private final InboxMessageRepository messages;
    private final InboxProperties properties;
    private final InboxMetrics metrics;
    private final LeadershipFence fence;
    private final Clock clock;

    public InboxOutcomes(InboxMessageRepository messages, InboxProperties properties, InboxMetrics metrics,
            LeadershipFence fence, Clock clock) {
        this.messages = messages;
        this.properties = properties;
        this.metrics = metrics;
        this.fence = fence;
        this.clock = clock;
    }

    @Transactional
    public void recordFailure(UUID id, InboxFailure failure) {
        InboxMessage message = messages.findById(id)
                .orElseThrow(() -> new IllegalStateException("в журнале входящих нет записи " + id));
        InboxLogContext.run(message.receipt(), () -> record(message, failure));
    }

    private void record(InboxMessage message, InboxFailure failure) {
        if (message.status() != InboxStatus.NEW) {
            log.warn("исход сообщения {} уже зафиксирован другим проходом: {}",
                    message.receipt().messageId(), message.status());
            return;
        }
        fence.holdUntilCommit();
        int attempt = message.attempts() + 1;
        Optional<Duration> delay = retryDelay(failure, attempt);
        if (delay.isPresent()) {
            message.markRetry(failure.reason(), clock.instant().plus(delay.get()));
            log.warn("сообщение {} отложено до повтора после попытки {}: {}",
                    message.receipt().messageId(), attempt, failure.reason());
            return;
        }
        reject(message, failure, attempt);
    }

    private Optional<Duration> retryDelay(InboxFailure failure, int attempt) {
        return failure instanceof InboxFailure.Transient
                ? properties.retry().delayAfterFailedAttempt(attempt) : Optional.empty();
    }

    private void reject(InboxMessage message, InboxFailure failure, int attempt) {
        String reason = failure instanceof InboxFailure.Transient
                ? "повторы исчерпаны на попытке " + attempt + ": " + failure.reason() : failure.reason();
        message.markFailed(reason);
        InboxReceipt receipt = message.receipt();
        log.error("сообщение {} отклонено окончательно: тип события {}, попытка {}, SQLSTATE {}: {}",
                receipt.messageId(), receipt.eventType(), attempt, failure.sqlState(), reason);
        metrics.countFailed(receipt.eventType());
    }
}
