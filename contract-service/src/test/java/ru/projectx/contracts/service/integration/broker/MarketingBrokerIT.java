package ru.projectx.contracts.service.integration.broker;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import ru.projectx.contracts.service.outbox.OutboundMessage;
import ru.projectx.contracts.service.outbox.PublishOutcome;
import ru.projectx.contracts.support.AmqpIntegrationTest;

@AmqpIntegrationTest
@TestPropertySource(properties = {
        "projectx.payment.enabled=true",
        "projectx.ordering.url=http://ordering.test",
        "projectx.ordering.product=rvl",
        "projectx.ordering.channel=rvl-lk",
        "projectx.ordering.token.uri=http://keycloak.test/token",
        "projectx.ordering.token.client-id=contracts-test",
        "projectx.ordering.token.client-secret=no-keycloak",
        "projectx.brokers.marketing.connection.tls.enabled=false",
        "projectx.brokers.marketing.document-fixes.exchange=rvl.exchange",
        "projectx.brokers.marketing.document-fixes.routing-key=rvl-contract-fixed"})
@Import(MarketingBrokerIT.MarketingVirtualHost.class)
class MarketingBrokerIT {

    private static final String MARKETING_VHOST = "marketing";
    private static final String FIXES_QUEUE = "rvl.event.order.topic";
    private static final long RECEIVE_TIMEOUT_MILLIS = 10_000;

    @TestConfiguration(proxyBeanMethods = false)
    static class MarketingVirtualHost {

        @Bean
        DynamicPropertyRegistrar marketingBroker(RabbitMQContainer container) throws IOException, InterruptedException {
            container.execInContainer("rabbitmqctl", "add_vhost", MARKETING_VHOST);
            container.execInContainer("rabbitmqctl", "set_permissions", "-p", MARKETING_VHOST,
                    container.getAdminUsername(), ".*", ".*", ".*");
            String prefix = "projectx.brokers.marketing.connection.";
            return registry -> {
                registry.add(prefix + "host", container::getHost);
                registry.add(prefix + "port", container::getAmqpPort);
                registry.add(prefix + "virtual-host", () -> MARKETING_VHOST);
                registry.add(prefix + "username", container::getAdminUsername);
                registry.add(prefix + "password", container::getAdminPassword);
            };
        }
    }

    @Autowired
    @MarketingBroker
    private CachingConnectionFactory marketingConnectionFactory;

    @Autowired
    @MarketingBroker
    private RabbitTemplate marketingTemplate;

    @Autowired
    private BrokerPublisher publisher;

    @Autowired
    private HealthContributorRegistry healthContributors;

    @Autowired
    private MeterRegistry meters;

    @BeforeEach
    void orderingDeclaresItsExchangeAndQueue() {
        RabbitAdmin admin = new RabbitAdmin((ConnectionFactory) marketingConnectionFactory);
        DirectExchange exchange = new DirectExchange("rvl.exchange");
        Queue queue = new Queue(FIXES_QUEUE);
        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("rvl-contract-fixed"));
        admin.purgeQueue(FIXES_QUEUE);
    }

    @Test
    void marketingConnection_isTheThirdBrokerInHealthAndConnectionMetrics() {
        Health health = ((HealthIndicator) healthContributors.getContributor("marketingBroker")).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("broker", "marketing").containsEntry("virtualHost", MARKETING_VHOST);
        assertThat(marketingConnectionFactory.createConnection().getDelegate().getClientProvidedName())
                .isEqualTo("contract-service:marketing");
        assertThat(meters.find("rabbitmq.connections").tag("name", "marketingBroker").gauge()).isNotNull();
    }

    @Test
    void documentFix_isConfirmedAndRoutedToTheProductExchangeWithTheConfiguredKeyAsIs() {
        String body = "{\"source\":\"rvl\",\"eventType\":\"fixed\",\"data\":{\"agreementId\":\"a-1\"}}";

        PublishOutcome outcome = publisher.publish(new OutboundMessage(UUID.randomUUID(), UUID.randomUUID(),
                "PaymentOrderFixed", body));

        assertThat(outcome).isInstanceOf(PublishOutcome.Delivered.class);
        Message received = marketingTemplate.receive(FIXES_QUEUE, RECEIVE_TIMEOUT_MILLIS);
        assertThat(received).isNotNull();
        assertThat(new String(received.getBody(), StandardCharsets.UTF_8)).isEqualTo(body);
        assertThat(received.getMessageProperties().getReceivedExchange()).isEqualTo("rvl.exchange");
        assertThat(received.getMessageProperties().getReceivedRoutingKey()).isEqualTo("rvl-contract-fixed");
    }

    @Test
    void documentFixWithoutBoundQueue_isReturnedAndNotDelivered() {
        new RabbitAdmin((ConnectionFactory) marketingConnectionFactory).deleteQueue(FIXES_QUEUE);

        PublishOutcome outcome = publisher.publish(new OutboundMessage(UUID.randomUUID(), UUID.randomUUID(),
                "PaymentOrderFixed", "{}"));

        assertThat(outcome).isNotInstanceOf(PublishOutcome.Delivered.class);
        assertThat(outcome.reason()).contains("NO_ROUTE");
    }
}
