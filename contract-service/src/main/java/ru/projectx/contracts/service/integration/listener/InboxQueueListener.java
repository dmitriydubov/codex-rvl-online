package ru.projectx.contracts.service.integration.listener;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.inbox.InboxReceipt;
import ru.projectx.contracts.exception.InboxEnvelopeException;
import ru.projectx.contracts.service.InboxLogContext;
import ru.projectx.contracts.service.inbox.InboxMetrics;
import ru.projectx.contracts.service.inbox.receipt.InboxReceiver;
import ru.projectx.contracts.service.inbox.receipt.ReceiptOutcome;

@Component
public class InboxQueueListener implements ChannelAwareMessageListener {

    private static final Logger log = LoggerFactory.getLogger(InboxQueueListener.class);

    private final TaskEnvelopeParser envelopes;
    private final InboxReceiver receiver;
    private final InboxStorageFailures failures;
    private final InboxMetrics metrics;

    public InboxQueueListener(TaskEnvelopeParser envelopes, InboxReceiver receiver, InboxStorageFailures failures,
            InboxMetrics metrics) {
        this.envelopes = envelopes;
        this.receiver = receiver;
        this.failures = failures;
        this.metrics = metrics;
    }

    @Override
    public void onMessage(Message message, Channel channel) {
        TaskDelivery delivery = new TaskDelivery(message, channel);
        try {
            receive(delivery);
        }
        catch (IOException | ShutdownSignalException channelClosed) {
            log.warn("ответ брокеру по сообщению очереди {} не отправлен: {}",
                    delivery.queue(), channelClosed.getClass().getName());
        }
    }

    private void receive(TaskDelivery delivery) throws IOException {
        TaskEnvelope envelope;
        try {
            envelope = envelopes.parse(delivery.body());
        }
        catch (InboxEnvelopeException unusable) {
            lose(delivery, unusable);
            return;
        }
        InboxLogContext.run(envelope.receipt(), () -> accept(delivery, envelope));
    }

    private void accept(TaskDelivery delivery, TaskEnvelope envelope) throws IOException {
        switch (envelope) {
            case TaskEnvelope.Storable storable -> store(delivery, storable.receipt());
            case TaskEnvelope.Unstorable unstorable ->
                    failures.quarantine(delivery, unstorable.receipt(), UnstorableText.REASON);
        }
    }

    private void store(TaskDelivery delivery, InboxReceipt receipt) throws IOException {
        ReceiptOutcome outcome;
        try {
            outcome = receiver.receive(receipt, delivery.payload());
        }
        catch (RuntimeException storageFailure) {
            failures.handle(delivery, receipt, storageFailure);
            return;
        }
        log.debug("сообщение {} из очереди {}: {}", receipt.messageId(), delivery.queue(), outcome);
        delivery.acknowledge();
    }

    private void lose(TaskDelivery delivery, InboxEnvelopeException unusable) throws IOException {
        log.error("сообщение очереди {} потеряно: {} ({} байт)", delivery.queue(), unusable.getMessage(),
                delivery.body().length);
        metrics.countLost(unusable.lossReason());
        delivery.acknowledge();
    }
}
