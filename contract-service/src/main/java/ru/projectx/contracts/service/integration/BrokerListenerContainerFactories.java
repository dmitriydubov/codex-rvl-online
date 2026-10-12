package ru.projectx.contracts.service.integration;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import ru.projectx.contracts.config.BrokerProperties;

final class BrokerListenerContainerFactories {

    private static final int SINGLE_CONSUMER = 1;
    private static final int ONE_MESSAGE_IN_FLIGHT = 1;

    private BrokerListenerContainerFactories() {
    }

    static SimpleRabbitListenerContainerFactory create(
            String brokerName, ConnectionFactory connectionFactory, BrokerProperties broker) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(SINGLE_CONSUMER);
        factory.setPrefetchCount(ONE_MESSAGE_IN_FLIGHT);
        factory.setMissingQueuesFatal(true);
        factory.setAutoStartup(broker.listenerAutoStartup());
        factory.setTaskExecutor(new VirtualThreadTaskExecutor("rabbit-" + brokerName + "-"));
        return factory;
    }
}
