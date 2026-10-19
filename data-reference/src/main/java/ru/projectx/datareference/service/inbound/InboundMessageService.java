package ru.projectx.datareference.service.inbound;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.config.InboundRetryProperties;
import ru.projectx.datareference.entity.AwaitedParent;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundOutcome;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.metrics.InboundTransitionMetrics;

@Service
public class InboundMessageService {

    private static final Logger log = LoggerFactory.getLogger(InboundMessageService.class);

    private final InboundMessageRepository repository;
    private final InboundRetryProperties retryProperties;
    private final Clock clock;
    private final InboundTransitionMetrics transitions;
    private final FinalRejectionJournal rejections;
    private final Map<OneCStream, RejectionScope> scopes;

    public InboundMessageService(InboundMessageRepository repository, InboundRetryProperties retryProperties,
            Clock clock, InboundTransitionMetrics transitions, FinalRejectionJournal rejections,
            List<RejectionScope> scopes) {

        this.repository = repository;
        this.retryProperties = retryProperties;
        this.clock = clock;
        this.transitions = transitions;
        this.rejections = rejections;
        this.scopes = scopes.stream()
                .collect(Collectors.toUnmodifiableMap(RejectionScope::stream, Function.identity()));
    }

    @Transactional
    public InboundMessage store(UUID messageId, OneCStream stream, String sourceQueue, String payload) {
        InboundReceipt receipt = new InboundReceipt(messageId, stream, sourceQueue, clock.instant());
        return repository.save(InboundMessage.receive(receipt, payload));
    }

    @Transactional
    public void markProcessed(UUID messageId) {
        InboundMessage message = require(messageId);
        message.markProcessed(clock.instant());
        transitions.record(message);
    }

    @Transactional
    public void markSkipped(UUID messageId, String reason) {
        InboundMessage message = require(messageId);
        message.markSkipped(reason, clock.instant());
        transitions.record(message);
    }

    @Transactional
    public void markAwaitingParent(UUID messageId, AwaitedParent parent, @Nullable UUID record) {
        InboundMessage message = require(messageId);
        message.markAwaitingParent(parent, record, clock.instant());
        transitions.record(message);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InboundStatus recordTemporaryFailure(UUID messageId, InboundOutcome beforeAttempt,
            ParseFailure.Temporary failure) {

        InboundMessage message = require(messageId);
        if (isOutcomeCommitted(message, beforeAttempt)) {
            return keepCommitted(message, failure);
        }
        int failedAttempt = failedAttempt(message);
        List<InboundMessage> changed = retryProperties.delayBeforeAttempt(failedAttempt)
                .map(delay -> {
                    message.markRetrying(failure.reason(), clock.instant().plus(delay));
                    return List.of(message);
                })
                .orElseGet(() -> fail(message,
                        "повторы исчерпаны на попытке %d: %s".formatted(failedAttempt, failure.reason()), failure));
        changed.forEach(transitions::record);
        return message.outcome().status();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InboundStatus recordPermanentFailure(UUID messageId, InboundOutcome beforeAttempt,
            ParseFailure.Permanent failure) {

        InboundMessage message = require(messageId);
        if (isOutcomeCommitted(message, beforeAttempt)) {
            return keepCommitted(message, failure);
        }
        fail(message, failure.reason(), failure).forEach(transitions::record);
        return message.outcome().status();
    }

    @Transactional
    public List<InboundMessage> transferParts(UUID correlationId) {
        return repository.findByCorrelationId(correlationId);
    }

    @Transactional
    public void saveTransferPart(InboundMessage message) {
        repository.save(message);
        transitions.record(message);
    }

    private static boolean isOutcomeCommitted(InboundMessage message, InboundOutcome beforeAttempt) {
        return !message.outcome().equals(beforeAttempt);
    }

    private InboundStatus keepCommitted(InboundMessage message, ParseFailure failure) {
        InboundReceipt receipt = message.receipt();
        InboundStatus committed = message.outcome().status();
        log.warn("отказ после фиксации исхода сообщения {} потока {}: исход {} сохранён, повтора не будет, отказ {}, "
                + "SQLSTATE {}", receipt.messageId(), receipt.stream(), committed, failure.type(), failure.sqlState());
        transitions.record(message);
        return committed;
    }

    private static int failedAttempt(InboundMessage message) {
        return message.outcome().attempt() + 1;
    }

    private List<InboundMessage> fail(InboundMessage message, String reason, ParseFailure failure) {
        int failedAttempt = failedAttempt(message);
        List<InboundMessage> rejected = Optional.ofNullable(scopes.get(message.receipt().stream()))
                .map(scope -> scope.reject(message, reason))
                .orElseGet(() -> {
                    message.markFailed(reason);
                    return List.of(message);
                });
        rejections.rejected(message.receipt(), failedAttempt, failure);
        return rejected;
    }

    private InboundMessage require(UUID messageId) {
        return repository.findById(messageId)
                .orElseThrow(() -> new IllegalStateException("нет строки сырья для сообщения " + messageId));
    }
}
