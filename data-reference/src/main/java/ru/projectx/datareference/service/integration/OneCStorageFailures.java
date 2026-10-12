package ru.projectx.datareference.service.integration;

import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.IngestProperties;
import ru.projectx.datareference.service.QuarantineService;

@Component
class OneCStorageFailures {

    private static final Logger log = LoggerFactory.getLogger(OneCStorageFailures.class);

    private final QuarantineService quarantine;
    private final OneCConsumerPause consumers;
    private final IngestProperties properties;
    private final OneCIngestMetrics metrics;

    OneCStorageFailures(QuarantineService quarantine, OneCConsumerPause consumers, IngestProperties properties,
            OneCIngestMetrics metrics) {

        this.quarantine = quarantine;
        this.consumers = consumers;
        this.properties = properties;
        this.metrics = metrics;
    }

    void handle(OneCDelivery delivery, UUID messageId, RuntimeException failure) throws IOException {
        switch (OneCStorageFailure.of(failure)) {
            case OneCStorageFailure.Poison poison -> quarantine(delivery, messageId, poison.sqlState());
            case OneCStorageFailure.DuplicateRace ignored -> returnAfterPause(delivery, messageId, failure);
            case OneCStorageFailure.Outage outage -> pauseConsumers(delivery, messageId, outage.outage());
        }
    }

    private void quarantine(OneCDelivery delivery, UUID messageId, String sqlState) throws IOException {
        if (storeInQuarantine(delivery, messageId, sqlState)) {
            metrics.record(delivery, IngestOutcome.QUARANTINED);
            delivery.acknowledge();
        }
    }

    private boolean storeInQuarantine(OneCDelivery delivery, UUID messageId, String sqlState) {
        try {
            reportQuarantined(quarantine.quarantine(delivery.rejected(messageId), sqlState), delivery, messageId, sqlState);
            return true;
        }
        catch (RuntimeException storageFailure) {
            return quarantinedByConcurrentDelivery(delivery, messageId, storageFailure);
        }
    }

    private boolean quarantinedByConcurrentDelivery(
            OneCDelivery delivery, UUID messageId, RuntimeException storageFailure) {

        if (OneCStorageFailure.of(storageFailure) instanceof OneCStorageFailure.DuplicateRace) {
            log.debug("сообщение {} уже положила в карантин параллельная доставка", messageId);
            return true;
        }
        pauseConsumers(delivery, messageId, OneCStorageFailure.Outage.of(storageFailure).outage());
        return false;
    }

    private static void reportQuarantined(boolean quarantined, OneCDelivery delivery, UUID messageId, String sqlState) {
        if (quarantined) {
            log.error("сообщение {} из очереди {} в карантине: база не принимает его тело, SQLSTATE {} ({} байт)",
                    messageId, delivery.queue(), sqlState, delivery.rawBody().length);
        }
    }

    private void returnAfterPause(OneCDelivery delivery, UUID messageId, RuntimeException failure) throws IOException {
        log.warn("сырьё сообщения {} не сохранено, вернём источнику через {}: {}",
                messageId, properties.duplicateDeliveryBackoff(), failure.getClass().getName());
        pauseBeforeRedelivery();
        metrics.record(delivery, IngestOutcome.RETURNED);
        delivery.returnToQueue();
    }

    private void pauseConsumers(OneCDelivery delivery, UUID messageId, StorageOutage outage) {
        metrics.record(delivery, IngestOutcome.STORAGE_OUTAGE);
        log.warn("сырьё сообщения {} не сохранено: {} SQLSTATE {}", messageId, outage.failureType(), outage.sqlState());
        consumers.pause(outage);
    }

    private void pauseBeforeRedelivery() {
        try {
            Thread.sleep(properties.duplicateDeliveryBackoff());
        }
        catch (InterruptedException shuttingDown) {
            Thread.currentThread().interrupt();
        }
    }
}
