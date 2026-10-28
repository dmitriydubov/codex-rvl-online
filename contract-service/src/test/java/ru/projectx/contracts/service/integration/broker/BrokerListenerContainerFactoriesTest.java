package ru.projectx.contracts.service.integration.broker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

class BrokerListenerContainerFactoriesTest {

    private final CachingConnectionFactory connectionFactory = new CachingConnectionFactory("broker.example");

    @AfterEach
    void releaseConnectionFactory() {
        connectionFactory.destroy();
    }

    @Test
    void container_acknowledgesManuallyOneMessageAtATimeAndFailsWithoutQueue() {
        SimpleMessageListenerContainer container = container();

        assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.MANUAL);
        assertThat(container.getConnectionFactory()).isSameAs(connectionFactory);
        assertThat(container)
                .hasFieldOrPropertyWithValue("concurrentConsumers", 1)
                .hasFieldOrPropertyWithValue("prefetchCount", 1)
                .hasFieldOrPropertyWithValue("missingQueuesFatal", true);
    }

    @Test
    void factory_setsConsumerCountAndMissingQueuesExplicitly() {
        SimpleRabbitListenerContainerFactory factory = factory();

        assertThat(factory)
                .hasFieldOrPropertyWithValue("concurrentConsumers", 1)
                .hasFieldOrPropertyWithValue("missingQueuesFatal", true);
    }

    @Test
    void endpointWithoutAutoStartup_givesAContainerThatWaitsForAnExplicitStart() {
        SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
        endpoint.setId("test-endpoint");
        endpoint.setQueueNames("test-queue");
        endpoint.setMessageListener(message -> { });
        endpoint.setAutoStartup(false);

        assertThat(factory().createListenerContainer(endpoint).isAutoStartup()).isFalse();
    }

    @Test
    void consumers_runOnVirtualThreadsNamedAfterTheBroker() {
        Object executor = ReflectionTestUtils.getField(container(), "taskExecutor");

        assertThat(executor).isInstanceOf(VirtualThreadTaskExecutor.class);
        assertThat(((VirtualThreadTaskExecutor) executor).getVirtualThreadFactory().newThread(() -> { }).getName())
                .startsWith("rabbit-onec-");
    }

    private SimpleRabbitListenerContainerFactory factory() {
        return BrokerListenerContainerFactories.create("onec", connectionFactory);
    }

    private SimpleMessageListenerContainer container() {
        SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
        endpoint.setId("test-endpoint");
        endpoint.setQueueNames("test-queue");
        endpoint.setMessageListener(message -> { });
        return factory().createListenerContainer(endpoint);
    }
}
