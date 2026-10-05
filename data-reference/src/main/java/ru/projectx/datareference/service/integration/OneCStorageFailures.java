package ru.projectx.datareference.service.integration;

import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.IngestProperties;
import ru.projectx.datareference.service.QuarantineService;

/**
 * Что делать с сообщением 1С:УСК, сырьё которого база записать не смогла. Решение зависит от кода SQLSTATE
 * отказа ({@link OneCStorageFailure}):
 * <ul>
 *   <li>тело не принимает сама база — сообщение уходит в карантин байт в байт и подтверждается брокеру,
 *       очередь идёт дальше; в журнале ERROR с причиной, но без тела;</li>
 *   <li>две доставки одного сообщения столкнулись на записи — пауза и возврат в очередь: повтор примет
 *       сообщение как дубль;</li>
 *   <li>всё остальное — временный отказ хранилища: потребители очередей встают на паузу, а сообщение остаётся
 *       неподтверждённым и вернётся в очередь при остановке контейнера.</li>
 * </ul>
 *
 * <p>В журнал идут тип исключения и код, но не текст ошибки: ошибка вставки печатает параметры запроса, а
 * этим путём проходят полисы с персональными данными. Исход доставки считает {@link OneCIngestMetrics}.
 */
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

    /** Отвечает брокеру за сообщение, сырьё которого не записалось из-за {@code failure}. */
    void handle(OneCDelivery delivery, UUID messageId, RuntimeException failure) throws IOException {
        switch (OneCStorageFailure.of(failure)) {
            case OneCStorageFailure.Poison poison -> quarantine(delivery, messageId, poison.sqlState());
            case OneCStorageFailure.DuplicateRace ignored -> returnAfterPause(delivery, messageId, failure);
            case OneCStorageFailure.Outage outage -> pauseConsumers(delivery, messageId, outage.outage());
        }
    }

    /**
     * Карантин — тоже запись в базу, и она тоже может не удаться: тогда это уже отказ хранилища, а не отравленное
     * тело (колонка bytea принимает любое тело), и потребители встают на паузу. Повторная доставка уже карантинного
     * сообщения второй записи не создаёт и просто подтверждается.
     */
    private void quarantine(OneCDelivery delivery, UUID messageId, String sqlState) throws IOException {
        if (storeInQuarantine(delivery, messageId, sqlState)) {
            metrics.record(delivery, IngestOutcome.QUARANTINED);
            delivery.acknowledge();
        }
    }

    /** {@code true} — сообщение в карантине (записано сейчас или раньше), можно подтверждать; {@code false} — пауза. */
    private boolean storeInQuarantine(OneCDelivery delivery, UUID messageId, String sqlState) {
        try {
            reportQuarantined(quarantine.quarantine(delivery.rejected(messageId), sqlState), delivery, messageId, sqlState);
            return true;
        }
        catch (RuntimeException storageFailure) {
            return quarantinedByConcurrentDelivery(delivery, messageId, storageFailure);
        }
    }

    /**
     * Две доставки одного отравленного сообщения могут писать карантин одновременно: обе не нашли записи, вторая
     * упала на ключе (23505). Запись уже есть — первая доставка её закоммитила, иначе ключ не столкнулся бы, — и
     * второй достаточно подтверждения. Любой другой отказ записи карантина — отказ хранилища.
     */
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

    /** Сообщение остаётся неподтверждённым намеренно: в очередь его вернёт остановка контейнера. */
    private void pauseConsumers(OneCDelivery delivery, UUID messageId, StorageOutage outage) {
        metrics.record(delivery, IngestOutcome.STORAGE_OUTAGE);
        log.warn("сырьё сообщения {} не сохранено: {} SQLSTATE {}", messageId, outage.failureType(), outage.sqlState());
        consumers.pause(outage);
    }

    /**
     * Держит паузу перед возвратом сообщения. Прерывание — это остановка контейнера:
     * флаг восстанавливаем и возвращаем сообщение сразу, канал ещё открыт.
     */
    private void pauseBeforeRedelivery() {
        try {
            Thread.sleep(properties.duplicateDeliveryBackoff());
        }
        catch (InterruptedException shuttingDown) {
            Thread.currentThread().interrupt();
        }
    }
}
