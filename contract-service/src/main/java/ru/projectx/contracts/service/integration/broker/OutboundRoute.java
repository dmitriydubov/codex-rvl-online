package ru.projectx.contracts.service.integration.broker;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

record OutboundRoute(RabbitTemplate template, String exchange, String routingKey) {

    private static final String DEFAULT_EXCHANGE = "";

    static OutboundRoute toQueue(RabbitTemplate template, String queue) {
        return new OutboundRoute(template, DEFAULT_EXCHANGE, queue);
    }

    void send(Message message, CorrelationData correlation) {
        template.send(exchange, routingKey, message, correlation);
    }
}
