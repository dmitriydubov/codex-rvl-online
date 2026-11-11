package ru.projectx.contracts.service.integration.listener;

import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;

public record AdditionalInboxEndpoint(String queue, SimpleRabbitListenerContainerFactory containerFactory,
        MessageListener listener) {
}
