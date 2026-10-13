package ru.projectx.contracts.service.integration;

import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.InternalBrokerProperties;
import ru.projectx.contracts.config.OneCBrokerProperties;

@Component
public class InboxQueueEndpoints implements RabbitListenerConfigurer {

    private final InboxQueueListener listener;
    private final SimpleRabbitListenerContainerFactory internalFactory;
    private final InternalBrokerProperties internal;
    private final SimpleRabbitListenerContainerFactory onecFactory;
    private final OneCBrokerProperties onec;

    public InboxQueueEndpoints(InboxQueueListener listener,
            @InternalBroker SimpleRabbitListenerContainerFactory internalFactory, InternalBrokerProperties internal,
            @OneCBroker SimpleRabbitListenerContainerFactory onecFactory, OneCBrokerProperties onec) {
        this.listener = listener;
        this.internalFactory = internalFactory;
        this.internal = internal;
        this.onecFactory = onecFactory;
        this.onec = onec;
    }

    @Override
    public void configureRabbitListeners(RabbitListenerEndpointRegistrar registrar) {
        registrar.registerEndpoint(endpoint(internal.queues().taskChanges()), internalFactory);
        registrar.registerEndpoint(endpoint(onec.queues().lmPolicyIssues()), onecFactory);
    }

    SimpleRabbitListenerEndpoint endpoint(String queue) {
        SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
        endpoint.setId(queue);
        endpoint.setQueueNames(queue);
        endpoint.setMessageListener(listener);
        return endpoint;
    }
}
