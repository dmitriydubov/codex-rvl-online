package ru.projectx.contracts.service.integration;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.journal.InboxProperties;
import ru.projectx.contracts.entity.inbox.InboxReceipt;
import ru.projectx.contracts.service.inbox.InboxMetrics;
import ru.projectx.contracts.service.inbox.QuarantineService;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;

@Component
class InboxStorageFailures {

    private static final Logger log = LoggerFactory.getLogger(InboxStorageFailures.class);
    private static final String SQLSTATE = "SQLSTATE ";

    private final QuarantineService quarantine;
    private final InboxConsumerPause consumers;
    private final InboxProperties properties;
    private final InboxMetrics metrics;
    private final BackgroundLeadership leadership;
    private final Pauses pauses;

    InboxStorageFailures(QuarantineService quarantine, InboxConsumerPause consumers, InboxProperties properties,
            InboxMetrics metrics, BackgroundLeadership leadership, Pauses pauses) {
        this.quarantine = quarantine;
        this.consumers = consumers;
        this.properties = properties;
        this.metrics = metrics;
        this.leadership = leadership;
        this.pauses = pauses;
    }

    void handle(TaskDelivery delivery, InboxReceipt receipt, RuntimeException failure) throws IOException {
        switch (InboxStorageFailure.of(failure)) {
            case InboxStorageFailure.Poison poison -> quarantine(delivery, receipt, SQLSTATE + poison.sqlState());
            case InboxStorageFailure.DuplicateRace ignored -> returnAfterPause(delivery, receipt, failure);
            case InboxStorageFailure.Outage outage -> pauseConsumers(delivery, receipt, outage.outage());
            case InboxStorageFailure.LeadershipLost ignored -> standDown(delivery, receipt);
        }
    }

    void quarantine(TaskDelivery delivery, InboxReceipt receipt, String reason) throws IOException {
        try {
            recordQuarantine(quarantine.quarantine(delivery.rejected(receipt.messageId()), reason), delivery, receipt, reason);
        }
        catch (RuntimeException storageFailure) {
            quarantineFailed(delivery, receipt, storageFailure);
            return;
        }
        delivery.acknowledge();
    }

    private void recordQuarantine(boolean quarantined, TaskDelivery delivery, InboxReceipt receipt, String reason) {
        if (!quarantined) {
            log.debug("сообщение {} уже лежит в карантине, повтор подтверждён", receipt.messageId());
            return;
        }
        metrics.countQuarantined();
        log.error("сообщение {} из очереди {} помещено в карантин: {} ({} байт)", receipt.messageId(), delivery.queue(),
                reason, delivery.body().length);
    }

    private void quarantineFailed(TaskDelivery delivery, InboxReceipt receipt, RuntimeException failure)
            throws IOException {
        switch (InboxStorageFailure.of(failure)) {
            case InboxStorageFailure.DuplicateRace ignored -> acknowledgeQuarantinedElsewhere(delivery, receipt);
            case InboxStorageFailure.LeadershipLost ignored -> standDown(delivery, receipt);
            case InboxStorageFailure.Poison ignored -> pauseConsumers(delivery, receipt, StorageOutage.of(failure));
            case InboxStorageFailure.Outage outage -> pauseConsumers(delivery, receipt, outage.outage());
        }
    }

    private void acknowledgeQuarantinedElsewhere(TaskDelivery delivery, InboxReceipt receipt) throws IOException {
        log.debug("сообщение {} уже положила в карантин параллельная доставка", receipt.messageId());
        delivery.acknowledge();
    }

    private void returnAfterPause(TaskDelivery delivery, InboxReceipt receipt, RuntimeException failure)
            throws IOException {
        log.warn("сообщение {} из очереди {} столкнулось с параллельной доставкой, вернётся в очередь через {}: {}",
                receipt.messageId(), delivery.queue(), properties.duplicateDeliveryBackoff(),
                failure.getClass().getName());
        if (pauses.hold(properties.duplicateDeliveryBackoff())) {
            delivery.returnToQueue();
        }
    }

    private void standDown(TaskDelivery delivery, InboxReceipt receipt) {
        log.info("сообщение {} из очереди {} оставлено без ответа: экземпляр больше не ведущий, потребители "
                + "останавливаются, закрытие канала вернёт сообщение в очередь", receipt.messageId(), delivery.queue());
        consumers.stopConsumersWithoutProbe();
        leadership.stepDown();
    }

    private void pauseConsumers(TaskDelivery delivery, InboxReceipt receipt, StorageOutage outage) {
        log.warn("сообщение {} из очереди {} не сохранено в журнал входящих: {}, SQLSTATE {}", receipt.messageId(),
                delivery.queue(), outage.failureType(), outage.sqlState());
        consumers.pause(outage);
    }
}
