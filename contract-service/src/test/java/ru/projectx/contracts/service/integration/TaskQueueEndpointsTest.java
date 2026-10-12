package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import ru.projectx.contracts.config.BrokerConnectionProperties;
import ru.projectx.contracts.config.InternalBrokerProperties;

class TaskQueueEndpointsTest {

    private final TaskQueueListener listener = mock();
    private final SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    private final TaskQueueEndpoints endpoints = new TaskQueueEndpoints(listener, factory, broker());

    @Test
    void taskQueue_isRegisteredOnTheInternalBrokerFactoryWithTheListener() {
        RabbitListenerEndpointRegistrar registrar = mock();

        endpoints.configureRabbitListeners(registrar);

        ArgumentCaptor<SimpleRabbitListenerEndpoint> endpoint = ArgumentCaptor.captor();
        verify(registrar).registerEndpoint(endpoint.capture(), org.mockito.ArgumentMatchers.same(factory));
        assertThat(endpoint.getValue().getId()).isEqualTo("task-changes");
        assertThat(endpoint.getValue().getQueueNames()).containsExactly("task-changes");
        assertThat(endpoint.getValue().getMessageListener()).isSameAs(listener);
    }

    private static InternalBrokerProperties broker() {
        return new InternalBrokerProperties(new BrokerConnectionProperties("broker", 5672, "/", "user", "secret",
                Duration.ofSeconds(10), new BrokerConnectionProperties.Tls(false, true, true)), true,
                new InternalBrokerProperties.Queues("task-changes", "status-changes"));
    }
}
