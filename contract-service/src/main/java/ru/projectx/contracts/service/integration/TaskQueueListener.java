package ru.projectx.contracts.service.integration;

import com.rabbitmq.client.Channel;
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
import ru.projectx.contracts.service.inbox.InboxReceiver;
import ru.projectx.contracts.service.inbox.ReceiptOutcome;

@Component
public class TaskQueueListener implements ChannelAwareMessageListener {

    private static final Logger log = LoggerFactory.getLogger(TaskQueueListener.class);

    private final TaskEnvelopeParser envelopes;
    private final InboxReceiver receiver;
    private final InboxProperties properties;
    private final Pauses pauses;

    public TaskQueueListener(TaskEnvelopeParser envelopes, InboxReceiver receiver, InboxProperties properties,
            Pauses pauses) {
        this.envelopes = envelopes;
        this.receiver = receiver;
        this.properties = properties;
        this.pauses = pauses;
    }

    @Override
    public void onMessage(Message message, Channel channel) {
        TaskDelivery delivery = new TaskDelivery(message, channel);
        try {
            receive(delivery);
        }
        catch (IOException channelClosed) {
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
            lose(delivery, unusable.getMessage());
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
            handle(delivery, receipt, InboxStorageFailure.of(storageFailure));
            return;
        }
        log.debug("сообщение {} из очереди {}: {}", receipt.messageId(), delivery.queue(), outcome);
        delivery.acknowledge();
    }

    private void handle(TaskDelivery delivery, InboxReceipt receipt, InboxStorageFailure failure) throws IOException {
        if (failure.rejectedForever()) {
            lose(delivery, "база не принимает тело сообщения " + receipt.messageId() + ", SQLSTATE " + failure.sqlState());
            return;
        }
        log.warn("сообщение {} не сохранено в журнал входящих, вернётся в очередь через {}: {}, SQLSTATE {}",
                receipt.messageId(), properties.requeueDelay(), failure.failureType(), failure.sqlState());
        if (pauses.hold(properties.requeueDelay())) {
            delivery.returnToQueue();
        }
    }

    private static void lose(TaskDelivery delivery, String reason) throws IOException {
        log.error("сообщение очереди {} потеряно: {} ({} байт)", delivery.queue(), reason, delivery.body().length);
        delivery.acknowledge();
    }
}
