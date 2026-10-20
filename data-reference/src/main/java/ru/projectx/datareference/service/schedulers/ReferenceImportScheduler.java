package ru.projectx.datareference.service.schedulers;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.entity.inbound.InboundOutcome;
import ru.projectx.datareference.entity.inbound.InboundReceipt;
import ru.projectx.datareference.entity.inbound.InboundStatus;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.repository.inbound.InboundMessageRepository;
import ru.projectx.datareference.service.inbound.InboundMessageService;
import ru.projectx.datareference.service.inbound.OneCLogContext;
import ru.projectx.datareference.service.inbound.ParseFailure;
import ru.projectx.datareference.service.inbound.ParseFailures;
import ru.projectx.datareference.service.leadership.LeadershipLostException;
import ru.projectx.datareference.service.onec.ImportOutcome;
import ru.projectx.datareference.service.onec.ReferenceImportService;

@Component
public class ReferenceImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReferenceImportScheduler.class);

    private final InboundMessageRepository repository;
    private final ReferenceImportService importService;
    private final InboundMessageService inboundMessageService;
    private final ParseFailures failures;
    private final OverdueParentWaits overdueParentWaits;
    private final ImportProperties properties;
    private final Clock clock;

    public ReferenceImportScheduler(
            InboundMessageRepository repository,
            ReferenceImportService importService,
            InboundMessageService inboundMessageService,
            ParseFailures failures,
            OverdueParentWaits overdueParentWaits,
            ImportProperties properties,
            Clock clock) {

        this.repository = repository;
        this.importService = importService;
        this.inboundMessageService = inboundMessageService;
        this.failures = failures;
        this.overdueParentWaits = overdueParentWaits;
        this.properties = properties;
        this.clock = clock;
    }

    public void importPendingMessages() {
        List<InboundMessage> due = repository.findDueForImport(clock.instant(), Limit.of(properties.batchSize()));
        Map<OneCStream, UUID> heldStreams = new EnumMap<>(OneCStream.class);
        for (InboundMessage message : due) {
            importInStreamOrder(message, heldStreams);
        }
        overdueParentWaits.rejectAndReport();
    }

    private void importInStreamOrder(InboundMessage message, Map<OneCStream, UUID> heldStreams) {
        InboundReceipt receipt = message.receipt();
        Optional.ofNullable(heldStreams.get(receipt.stream())).ifPresentOrElse(
                head -> log.info("сообщение {} потока {} ждёт: раньше него в потоке сообщение {}, отложенное до повтора",
                        receipt.messageId(), receipt.stream(), head),
                () -> holdStreamIfLeftForRetry(message, heldStreams));
    }

    private void holdStreamIfLeftForRetry(InboundMessage message, Map<OneCStream, UUID> heldStreams) {
        InboundReceipt receipt = message.receipt();
        importOne(message)
                .filter(InboundStatus.RETRY::equals)
                .ifPresent(retry -> heldStreams.put(receipt.stream(), receipt.messageId()));
    }

    private Optional<InboundStatus> importOne(InboundMessage message) {
        InboundReceipt receipt = message.receipt();
        MDC.put(OneCLogContext.MESSAGE_ID, receipt.messageId().toString());
        MDC.put(OneCLogContext.STREAM, receipt.stream().name());
        try {
            return importAndClassify(message);
        }
        finally {
            MDC.remove(OneCLogContext.MESSAGE_ID);
            MDC.remove(OneCLogContext.STREAM);
        }
    }

    private Optional<InboundStatus> importAndClassify(InboundMessage message) {
        InboundReceipt receipt = message.receipt();
        InboundOutcome beforeAttempt = message.outcome();
        try {
            reportOutcome(receipt, importService.importMessage(message));
            return Optional.empty();
        }
        catch (RuntimeException failure) {
            return Optional.of(recordFailure(receipt, beforeAttempt, classifyUnlessLeadershipLost(failure)));
        }
    }

    private ParseFailure classifyUnlessLeadershipLost(RuntimeException failure) {
        if (failure instanceof LeadershipLostException lost) {
            throw lost;
        }
        return failures.classify(failure);
    }

    private InboundStatus recordFailure(InboundReceipt receipt, InboundOutcome beforeAttempt, ParseFailure failure) {
        return switch (failure) {
            case ParseFailure.Temporary temporary -> {
                log.warn("временная ошибка разбора сообщения {} потока {}: {}, SQLSTATE {}",
                        receipt.messageId(), receipt.stream(), temporary.type(), temporary.sqlState());
                yield inboundMessageService.recordTemporaryFailure(receipt.messageId(), beforeAttempt, temporary);
            }
            case ParseFailure.Permanent permanent ->
                    inboundMessageService.recordPermanentFailure(receipt.messageId(), beforeAttempt, permanent);
        };
    }

    private static void reportOutcome(InboundReceipt receipt, ImportOutcome outcome) {
        if (outcome.needsAttention()) {
            log.warn("сообщение {} потока {} {}", receipt.messageId(), receipt.stream(), outcome.summary());
            return;
        }
        log.info("сообщение {} потока {} {}", receipt.messageId(), receipt.stream(), outcome.summary());
    }
}
