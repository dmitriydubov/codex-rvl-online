package ru.projectx.datareference.service.integration;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCEnvelopeException;
import ru.projectx.datareference.service.OneCLogContext;
import ru.projectx.datareference.service.onec.OneCIngestService;

/**
 * Вход из брокера 1С:УСК: подтверждение уходит только после того, как приём зафиксирован в базе, иначе падение до
 * коммита теряло бы сообщение молча. Отказ записи разбирает {@link OneCStorageFailures}, а нечитаемый конверт
 * подтверждается и теряется. На время обработки в MDC лежат поток, очередь и {@code message-id}.
 */
@Component
public class OneCQueueListener {

    private static final Logger log = LoggerFactory.getLogger(OneCQueueListener.class);

    private final OneCEnvelopeParser envelopeParser;
    private final OneCIngestService ingestService;
    private final OneCStorageFailures storageFailures;
    private final OneCIngestMetrics metrics;

    public OneCQueueListener(OneCEnvelopeParser envelopeParser, OneCIngestService ingestService,
            OneCStorageFailures storageFailures, OneCIngestMetrics metrics) {

        this.envelopeParser = envelopeParser;
        this.ingestService = ingestService;
        this.storageFailures = storageFailures;
        this.metrics = metrics;
    }

    /** Принимает одно сообщение очереди потока {@code stream} и отвечает брокеру. */
    public void receive(OneCStream stream, Message message, Channel channel) throws IOException {
        OneCDelivery delivery = new OneCDelivery(stream, message, channel);
        MDC.put(OneCLogContext.STREAM, stream.name());
        MDC.put(OneCLogContext.QUEUE, delivery.queue());
        try {
            accept(delivery);
        }
        finally {
            MDC.remove(OneCLogContext.STREAM);
            MDC.remove(OneCLogContext.QUEUE);
        }
    }

    private void accept(OneCDelivery delivery) throws IOException {
        UUID messageId;
        try {
            messageId = envelopeParser.messageIdOf(delivery.body());
        }
        catch (OneCEnvelopeException unidentifiable) {
            dropUnidentifiable(delivery, unidentifiable);
            return;
        }
        MDC.put(OneCLogContext.MESSAGE_ID, messageId.toString());
        try {
            store(delivery, messageId);
        }
        finally {
            MDC.remove(OneCLogContext.MESSAGE_ID);
        }
    }

    /**
     * Подтверждение и счёт исхода стоят вне {@code try} намеренно: ни отказ канала на подтверждении, ни отказ
     * счётчика — не отказ записи. Сырьё уже закоммичено, и повторная запись дала бы дубль, а не потерю.
     */
    private void store(OneCDelivery delivery, UUID messageId) throws IOException {
        IngestOutcome outcome;
        try {
            outcome = ingest(delivery, messageId);
        }
        catch (RuntimeException storageFailure) {
            storageFailures.handle(delivery, messageId, storageFailure);
            return;
        }
        metrics.record(delivery, outcome);
        delivery.acknowledge();
    }

    /** Счёт исхода — снаружи, после записи: отказ счётчика не должен выглядеть отказом базы. */
    private IngestOutcome ingest(OneCDelivery delivery, UUID messageId) {
        if (ingestService.receive(messageId, delivery.stream(), delivery.queue(), delivery.payload())) {
            return IngestOutcome.STORED;
        }
        log.debug("сообщение {} из очереди {} уже принималось — дубль", messageId, delivery.queue());
        return IngestOutcome.DUPLICATE;
    }

    /**
     * Сообщение без читаемого {@code message-id} подтверждается и теряется: возврат в очередь дал бы бесконечную
     * передоставку, а придуманный ключ сломал бы дедупликацию. Единственный след — ERROR в журнале, без тела, потому
     * что внутри может оказаться полис.
     */
    private void dropUnidentifiable(OneCDelivery delivery, OneCEnvelopeException cause) throws IOException {
        metrics.record(delivery, IngestOutcome.UNIDENTIFIED);
        log.error("сообщение потока {} из очереди {} потеряно: {} ({} байт). "
                        + "Принять его нельзя — в конверте нет пригодного message-id",
                delivery.stream(), delivery.queue(), cause.getMessage(), delivery.rawBody().length);
        delivery.acknowledge();
    }
}
