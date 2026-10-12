package ru.projectx.contracts.service.integration;

import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.InternalBrokerProperties;

@Component
public class TaskQueueEndpoints implements RabbitListenerConfigurer {

    private final TaskQueueListener listener;
    private final SimpleRabbitListenerContainerFactory containerFactory;
    private final InternalBrokerProperties broker;

    public TaskQueueEndpoints(TaskQueueListener listener,
            @InternalBroker SimpleRabbitListenerContainerFactory containerFactory, InternalBrokerProperties broker) {
        this.listener = listener;
        this.containerFactory = containerFactory;
        this.broker = broker;
    }

    @Override
    public void configureRabbitListeners(RabbitListenerEndpointRegistrar registrar) {
        registrar.registerEndpoint(endpoint(broker.queues().taskChanges()), containerFactory);
    }

    SimpleRabbitListenerEndpoint endpoint(String queue) {
        SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
        endpoint.setId(queue);
        endpoint.setQueueNames(queue);
        endpoint.setMessageListener(listener);
        return endpoint;
    }
}
