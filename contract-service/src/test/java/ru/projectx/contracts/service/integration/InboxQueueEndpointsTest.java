package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import ru.projectx.contracts.config.broker.BrokerConnectionProperties;
import ru.projectx.contracts.config.broker.InternalBrokerProperties;
import ru.projectx.contracts.config.broker.OneCBrokerProperties;

class InboxQueueEndpointsTest {

    private final InboxQueueListener listener = mock();
    private final SimpleRabbitListenerContainerFactory internalFactory = new SimpleRabbitListenerContainerFactory();
    private final SimpleRabbitListenerContainerFactory onecFactory = new SimpleRabbitListenerContainerFactory();
    private final InboxQueueEndpoints endpoints = new InboxQueueEndpoints(listener, internalFactory, internal(),
            onecFactory, onec());

    @Test
    void taskQueue_isRegisteredOnTheInternalBrokerFactoryWithTheListener() {
        RabbitListenerEndpointRegistrar registrar = mock();

        endpoints.configureRabbitListeners(registrar);

        ArgumentCaptor<SimpleRabbitListenerEndpoint> endpoint = ArgumentCaptor.captor();
        verify(registrar).registerEndpoint(endpoint.capture(), same(internalFactory));
        assertThat(endpoint.getValue().getId()).isEqualTo("task-changes");
        assertThat(endpoint.getValue().getQueueNames()).containsExactly("task-changes");
        assertThat(endpoint.getValue().getMessageListener()).isSameAs(listener);
        assertThat(endpoint.getValue().getAutoStartup()).as("контейнеры запускает роль ведущего").isFalse();
    }

    @Test
    void oneCResultQueues_areRegisteredOnTheOneCBrokerFactoryWithTheSameListener() {
        RabbitListenerEndpointRegistrar registrar = mock();

        endpoints.configureRabbitListeners(registrar);

        ArgumentCaptor<SimpleRabbitListenerEndpoint> endpoint = ArgumentCaptor.captor();
        verify(registrar, times(2)).registerEndpoint(endpoint.capture(), same(onecFactory));
        assertThat(endpoint.getAllValues()).extracting(SimpleRabbitListenerEndpoint::getId)
                .containsExactly("lm-policy-issues", "km-contract-registrations");
        assertThat(endpoint.getAllValues()).flatExtracting(SimpleRabbitListenerEndpoint::getQueueNames)
                .containsExactly("lm-policy-issues", "km-contract-registrations");
        assertThat(endpoint.getAllValues()).extracting(SimpleRabbitListenerEndpoint::getMessageListener)
                .containsOnly(listener);
        assertThat(endpoint.getAllValues()).extracting(SimpleRabbitListenerEndpoint::getAutoStartup).containsOnly(false);
    }

    private static InternalBrokerProperties internal() {
        return new InternalBrokerProperties(connection(), true,
                new InternalBrokerProperties.Queues("task-changes", "status-changes"));
    }

    private static OneCBrokerProperties onec() {
        return new OneCBrokerProperties(connection(), true, new OneCBrokerProperties.Queues(
                "km-contract-submissions", "km-contract-registrations", "lm-policy-requests", "lm-policy-issues"));
    }

    private static BrokerConnectionProperties connection() {
        return new BrokerConnectionProperties("broker", 5672, "/", "user", "secret", Duration.ofSeconds(10),
                new BrokerConnectionProperties.Tls(false, true, true));
    }
}
