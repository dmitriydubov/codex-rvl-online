package ru.projectx.contracts.service.integration.broker;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.journal.OutboxProperties;
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
        return routes.of(message.eventType())
                .map(route -> send(route, message))
                .orElseGet(() -> new PublishOutcome.Refused("для типа события " + message.eventType() + " нет очереди"));
    }

    @Override
    public Collection<Set<String>> eventTypesByBroker() {
        return routes.eventTypesByBroker();
    }

    private PublishOutcome send(OutboundRoute route, OutboundMessage message) {
        CorrelationData correlation = new CorrelationData(message.messageId().toString());
        try {
            route.send(amqpMessage(message), correlation);
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
        return MessageBuilder.withBody(message.payload().getBytes(StandardCharsets.UTF_8))
                .andProperties(properties)
                .build();
    }
}
