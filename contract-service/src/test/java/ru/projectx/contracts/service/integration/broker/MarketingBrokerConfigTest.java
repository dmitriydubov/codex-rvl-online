package ru.projectx.contracts.service.integration.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.config.broker.BrokerConnectionProperties;
import ru.projectx.contracts.config.broker.MarketingBrokerProperties;

class MarketingBrokerConfigTest {

    private final MarketingBrokerConfig config = new MarketingBrokerConfig();
    private final CachingConnectionFactory connectionFactory = new CachingConnectionFactory("marketing.example");

    @AfterEach
    void releaseConnectionFactory() {
        connectionFactory.destroy();
    }

    @Test
    void listenerContainers_readTheMarketingBrokerOneMessageAtATimeWithManualAcknowledgement() {
        SimpleMessageListenerContainer container = container();

        assertThat(container.getConnectionFactory()).isSameAs(connectionFactory);
        assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.MANUAL);
        assertThat(container)
                .hasFieldOrPropertyWithValue("concurrentConsumers", 1)
                .hasFieldOrPropertyWithValue("prefetchCount", 1)
                .hasFieldOrPropertyWithValue("missingQueuesFatal", true);
    }

    @Test
    void listenerContainers_runOnVirtualThreadsNamedAfterTheMarketingBroker() {
        Object executor = ReflectionTestUtils.getField(container(), "taskExecutor");

        assertThat(executor).isInstanceOf(VirtualThreadTaskExecutor.class);
        assertThat(((VirtualThreadTaskExecutor) executor).getVirtualThreadFactory().newThread(() -> { }).getName())
                .startsWith("rabbit-marketing-");
    }

    @Test
    void documentFixes_areRoutedToTheProductExchangeWithTheConfiguredKeyOnTheMarketingTemplate() {
        RabbitTemplate template = mock();

        AdditionalRoute route = config.documentFixesRoute(template, new MarketingBrokerProperties(
                new BrokerConnectionProperties("marketing", 5672, "/", "user", "secret", Duration.ofSeconds(10),
                        new BrokerConnectionProperties.Tls(false, true, true)), true,
                new MarketingBrokerProperties.DocumentFixes("rvl.exchange", "rvl-contract-fixed"),
                new MarketingBrokerProperties.OrderEvents("order.event.rvl.topic")));

        assertThat(route).isEqualTo(new AdditionalRoute("PaymentOrderFixed",
                new OutboundRoute(template, "rvl.exchange", "rvl-contract-fixed")));
    }

    private SimpleMessageListenerContainer container() {
        SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
        endpoint.setId("order-events");
        endpoint.setQueueNames("order.event.rvl.topic");
        endpoint.setMessageListener(message -> { });
        return config.marketingBrokerListenerContainerFactory(connectionFactory).createListenerContainer(endpoint);
    }
}
