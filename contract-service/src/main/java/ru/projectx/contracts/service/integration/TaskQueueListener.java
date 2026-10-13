package ru.projectx.contracts.service.integration;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.InboxProperties;
import ru.projectx.contracts.entity.InboxReceipt;
import ru.projectx.contracts.exception.InboxEnvelopeException;
import ru.projectx.contracts.service.InboxLogContext;
import ru.projectx.contracts.service.SqlStates;
import ru.projectx.contracts.service.inbox.InboxMetrics;
import ru.projectx.contracts.service.inbox.InboxReceiver;
import ru.projectx.contracts.service.inbox.ReceiptOutcome;

@Component
public class TaskQueueListener implements ChannelAwareMessageListener {

    private static final Logger log = LoggerFactory.getLogger(TaskQueueListener.class);

    private final TaskEnvelopeParser envelopes;
    private final InboxReceiver receiver;
    private final InboxProperties properties;
    private final Pauses pauses;
    private final InboxMetrics metrics;

    public TaskQueueListener(TaskEnvelopeParser envelopes, InboxReceiver receiver, InboxProperties properties,
            Pauses pauses, InboxMetrics metrics) {
        this.envelopes = envelopes;
        this.receiver = receiver;
        this.properties = properties;
        this.pauses = pauses;
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
        InboxReceipt receipt;
        try {
            receipt = envelopes.parse(delivery.body());
        }
        catch (InboxEnvelopeException unusable) {
            lose(delivery, unusable);
            return;
        }
        InboxLogContext.run(receipt, () -> store(delivery, receipt));
    }

    private void store(TaskDelivery delivery, InboxReceipt receipt) throws IOException {
        ReceiptOutcome outcome;
        try {
            outcome = receiver.receive(receipt, delivery.payload());
        }
        catch (RuntimeException storageFailure) {
            returnAfterPause(delivery, receipt, storageFailure);
            return;
        }
        log.debug("сообщение {} из очереди {}: {}", receipt.messageId(), delivery.queue(), outcome);
        delivery.acknowledge();
    }

    private void returnAfterPause(TaskDelivery delivery, InboxReceipt receipt, RuntimeException storageFailure)
            throws IOException {
        log.warn("сообщение {} не сохранено в журнал входящих, вернётся в очередь через {}: {}, SQLSTATE {}",
                receipt.messageId(), properties.requeueDelay(), storageFailure.getClass().getName(),
                SqlStates.deepestOrNone(storageFailure));
        if (pauses.hold(properties.requeueDelay())) {
            delivery.returnToQueue();
        }
    }

    private void lose(TaskDelivery delivery, InboxEnvelopeException unusable) throws IOException {
        log.error("сообщение очереди {} потеряно: {} ({} байт)", delivery.queue(), unusable.getMessage(),
                delivery.body().length);
        metrics.countLost(unusable.lossReason());
        delivery.acknowledge();
    }
}
