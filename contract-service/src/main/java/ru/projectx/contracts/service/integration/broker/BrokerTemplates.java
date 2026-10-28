package ru.projectx.contracts.service.integration.broker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

final class BrokerTemplates {

    private static final Logger log = LoggerFactory.getLogger(BrokerTemplates.class);

    private BrokerTemplates() {
    }

    static RabbitTemplate create(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        template.setReturnsCallback(BrokerTemplates::logReturn);
        return template;
    }

    static void logReturn(ReturnedMessage returned) {
        log.warn("брокер вернул сообщение {} из очереди {}: {} ({})",
                returned.getMessage().getMessageProperties().getMessageId(), returned.getRoutingKey(),
                returned.getReplyText(), returned.getReplyCode());
    }
}
