package ru.projectx.contracts.service.integration.listener;

import java.util.List;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.broker.InternalBrokerProperties;
import ru.projectx.contracts.config.broker.OneCBrokerProperties;
import ru.projectx.contracts.service.integration.broker.InternalBroker;
import ru.projectx.contracts.service.integration.broker.OneCBroker;

@Component
public class InboxQueueEndpoints implements RabbitListenerConfigurer {

    private final InboxQueueListener listener;
    private final SimpleRabbitListenerContainerFactory internalFactory;
    private final InternalBrokerProperties internal;
    private final SimpleRabbitListenerContainerFactory onecFactory;
    private final OneCBrokerProperties onec;
    private final List<AdditionalInboxEndpoint> additional;

    public InboxQueueEndpoints(InboxQueueListener listener,
            @InternalBroker SimpleRabbitListenerContainerFactory internalFactory, InternalBrokerProperties internal,
            @OneCBroker SimpleRabbitListenerContainerFactory onecFactory, OneCBrokerProperties onec,
            List<AdditionalInboxEndpoint> additional) {
        this.additional = List.copyOf(additional);
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
        registrar.registerEndpoint(endpoint(onec.queues().kmContractRegistrations()), onecFactory);
        for (AdditionalInboxEndpoint extra : additional) {
            registrar.registerEndpoint(endpoint(extra.queue(), extra.listener()), extra.containerFactory());
        }
    }

    SimpleRabbitListenerEndpoint endpoint(String queue) {
        return endpoint(queue, listener);
    }

    private static SimpleRabbitListenerEndpoint endpoint(String queue, MessageListener queueListener) {
        SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
        endpoint.setId(queue);
        endpoint.setQueueNames(queue);
        endpoint.setMessageListener(queueListener);
        endpoint.setAutoStartup(false);
        return endpoint;
    }
}
