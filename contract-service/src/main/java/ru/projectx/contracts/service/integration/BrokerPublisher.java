package ru.projectx.contracts.service.integration;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.OutboxProperties;
import ru.projectx.contracts.service.outbox.OutboundMessage;
import ru.projectx.contracts.service.outbox.OutboundPublisher;
import ru.projectx.contracts.service.outbox.PublishOutcome;

@Component
public class BrokerPublisher implements OutboundPublisher {

    private final OutboundRoutes routes;
    private final OutboxProperties properties;

    public BrokerPublisher(OutboundRoutes routes, OutboxProperties properties) {
        this.routes = routes;
        this.properties = properties;
    }

    @Override
    public PublishOutcome publish(OutboundMessage message) {
        Optional<OutboundRoute> route = routes.of(message.eventType());
        if (route.isEmpty()) {
            return new PublishOutcome.Refused("для типа события " + message.eventType() + " нет очереди");
        }
        CorrelationData correlation = new CorrelationData(message.messageId().toString());
        try {
            route.get().send(amqpMessage(message), correlation);
        }
        catch (AmqpException unreachable) {
            return new PublishOutcome.NotSent("брокер недоступен: " + unreachable.getClass().getName());
        }
        return Confirmations.await(correlation, properties.confirmTimeout());
    }

    private static Message amqpMessage(OutboundMessage message) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(message.messageId().toString());
        properties.setCorrelationId(message.correlationId().toString());
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        return MessageBuilder.withBody(message.payload().getBytes(StandardCharsets.UTF_8))
                .andProperties(properties)
                .build();
    }
}
