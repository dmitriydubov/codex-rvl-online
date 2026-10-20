package ru.projectx.datareference.service.integration;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.exception.OneCEnvelopeException;
import ru.projectx.datareference.service.inbound.OneCLogContext;
import ru.projectx.datareference.service.onec.OneCIngestService;

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

    private IngestOutcome ingest(OneCDelivery delivery, UUID messageId) {
        if (ingestService.receive(messageId, delivery.stream(), delivery.queue(), delivery.payload())) {
            return IngestOutcome.STORED;
        }
        log.debug("сообщение {} из очереди {} уже принималось — дубль", messageId, delivery.queue());
        return IngestOutcome.DUPLICATE;
    }

    private void dropUnidentifiable(OneCDelivery delivery, OneCEnvelopeException cause) throws IOException {
        metrics.record(delivery, IngestOutcome.UNIDENTIFIED);
        log.error("сообщение потока {} из очереди {} потеряно: {} ({} байт). "
                        + "Принять его нельзя — в конверте нет пригодного message-id",
                delivery.stream(), delivery.queue(), cause.getMessage(), delivery.rawBody().length);
        delivery.acknowledge();
    }
}
