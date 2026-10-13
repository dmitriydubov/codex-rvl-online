package ru.projectx.contracts.service.integration;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

record OutboundRoute(RabbitTemplate template, String queue) {

    private static final String DEFAULT_EXCHANGE = "";

    void send(Message message, CorrelationData correlation) {
        template.send(DEFAULT_EXCHANGE, queue, message, correlation);
    }
}
