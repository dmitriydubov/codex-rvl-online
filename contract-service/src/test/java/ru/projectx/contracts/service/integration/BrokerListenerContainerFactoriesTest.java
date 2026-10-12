package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.config.BrokerConnectionProperties;
import ru.projectx.contracts.config.InternalBrokerProperties;

/**
 * Контейнер слушателя получает правила приёма сервиса: ручное подтверждение, один потребитель с одним сообщением
 * в работе, отказ запуска без очереди и автостарт из настроек брокера. Соединение здесь не открывается: контейнер
 * только создаётся, но не запускается. Число потребителей, prefetch, отказ без очереди и исполнитель у фабрики и
 * контейнера геттеров не имеют, поэтому они читаются из полей; наблюдаемы они только на запущенном брокере.
 */
class BrokerListenerContainerFactoriesTest {

    private static final int PORT = 5672;

    private final CachingConnectionFactory connectionFactory = new CachingConnectionFactory("broker.example");

    @AfterEach
    void releaseConnectionFactory() {
        connectionFactory.destroy();
    }

    @Test
    void container_acknowledgesManuallyOneMessageAtATimeAndFailsWithoutQueue() {
        SimpleMessageListenerContainer container = container(true);

        assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.MANUAL);
        assertThat(container.getConnectionFactory()).isSameAs(connectionFactory);
        assertThat(container)
                .hasFieldOrPropertyWithValue("concurrentConsumers", 1)
                .hasFieldOrPropertyWithValue("prefetchCount", 1)
                .hasFieldOrPropertyWithValue("missingQueuesFatal", true);
    }

    /** Фабрика задаёт потребителя и отказ без очереди явно: умолчания библиотеки совпадают, но могут измениться. */
    @Test
    void factory_setsConsumerCountAndMissingQueuesExplicitly() {
        SimpleRabbitListenerContainerFactory factory = factory(true);

        assertThat(factory)
                .hasFieldOrPropertyWithValue("concurrentConsumers", 1)
                .hasFieldOrPropertyWithValue("missingQueuesFatal", true);
    }

    @Test
    void autoStartup_comesFromBrokerSettings() {
        assertThat(container(true).isAutoStartup()).isTrue();
        assertThat(container(false).isAutoStartup()).isFalse();
    }

    @Test
    void consumers_runOnVirtualThreadsNamedAfterTheBroker() {
        Object executor = ReflectionTestUtils.getField(container(true), "taskExecutor");

        assertThat(executor).isInstanceOf(VirtualThreadTaskExecutor.class);
        assertThat(((VirtualThreadTaskExecutor) executor).getVirtualThreadFactory().newThread(() -> { }).getName())
                .startsWith("rabbit-onec-");
    }

    private SimpleRabbitListenerContainerFactory factory(boolean autoStartup) {
        InternalBrokerProperties broker = new InternalBrokerProperties(
                new BrokerConnectionProperties("broker.example", PORT, "/", "contract_service", "secret",
                        Duration.ofSeconds(1), new BrokerConnectionProperties.Tls(false, true, true)),
                autoStartup,
                new InternalBrokerProperties.Queues("in", "out"));
        return BrokerListenerContainerFactories.create("onec", connectionFactory, broker);
    }

    private SimpleMessageListenerContainer container(boolean autoStartup) {
        SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
        endpoint.setId("test-endpoint");
        endpoint.setQueueNames("test-queue");
        endpoint.setMessageListener(message -> { });
        return factory(autoStartup).createListenerContainer(endpoint);
    }
}
