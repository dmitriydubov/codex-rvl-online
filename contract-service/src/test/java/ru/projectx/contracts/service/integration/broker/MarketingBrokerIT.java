package ru.projectx.contracts.service.integration.broker;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import ru.projectx.contracts.config.broker.MarketingBrokerProperties;
import ru.projectx.contracts.entity.inbox.InboxReceipt;
import ru.projectx.contracts.entity.inbox.InboxStatus;
import ru.projectx.contracts.repository.journal.InboxMessageRepository;
import ru.projectx.contracts.service.outbox.OutboundMessage;
import ru.projectx.contracts.service.outbox.PublishOutcome;
import ru.projectx.contracts.support.AmqpIntegrationTest;
import ru.projectx.contracts.support.TestBroker;
import ru.projectx.contracts.support.TestQueues;

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
        "projectx.brokers.marketing.listener-auto-startup=true",
        "projectx.brokers.marketing.document-fixes.exchange=rvl.exchange",
        "projectx.brokers.marketing.document-fixes.routing-key=rvl-contract-fixed",
        "projectx.brokers.marketing.order-events.queue=" + MarketingBrokerIT.ORDER_EVENTS_QUEUE})
@Import(MarketingBrokerIT.MarketingConnection.class)
class MarketingBrokerIT {

    static final String ORDER_EVENTS_QUEUE = "order.event.rvl.topic";

    private static final String MARKETING_VHOST = "marketing";
    private static final String FIXES_QUEUE = "rvl.event.order.topic";
    private static final long RECEIVE_TIMEOUT_MILLIS = 10_000;
    private static final Duration PATIENCE = Duration.ofSeconds(15);
    private static final String EVENT_ID = "d1a0c001-5e2f-4a3b-8c4d-000000000001";
    private static final String DOCUMENT_ID = "0196f0a2-0000-7000-8000-000000000011";
    private static final String CONTAINER_BROKER = "'${" + TestBroker.URL_PROPERTY + ":}'.isEmpty()";
    private static final String EXTERNAL_BROKER = "!" + CONTAINER_BROKER;

    @TestConfiguration(proxyBeanMethods = false)
    static class MarketingConnection {

        @Bean
        @ConditionalOnExpression(CONTAINER_BROKER)
        DynamicPropertyRegistrar marketingVirtualHostOfTheContainer(RabbitMQContainer container)
                throws IOException, InterruptedException {
            container.execInContainer("rabbitmqctl", "add_vhost", MARKETING_VHOST);
            container.execInContainer("rabbitmqctl", "set_permissions", "-p", MARKETING_VHOST,
                    container.getAdminUsername(), ".*", ".*", ".*");
            return marketing(new TestBroker.Address(container.getHost(), container.getAmqpPort(), MARKETING_VHOST,
                    container.getAdminUsername(), container.getAdminPassword()));
        }

        @Bean
        @ConditionalOnExpression(EXTERNAL_BROKER)
        DynamicPropertyRegistrar marketingNamesOnTheExternalBroker(Environment environment) {
            return marketing(TestBroker.Address.parse(environment.getRequiredProperty(TestBroker.URL_PROPERTY)));
        }

        @Bean
        @MarketingBroker
        TestQueues marketingBrokerQueues(@MarketingBroker ConnectionFactory connections,
                MarketingBrokerProperties properties) {
            return new TestQueues(connections).declare(properties.orderEvents().queue());
        }

        private static DynamicPropertyRegistrar marketing(TestBroker.Address address) {
            String prefix = "projectx.brokers.marketing.connection.";
            return registry -> {
                registry.add(prefix + "host", address::host);
                registry.add(prefix + "port", address::port);
                registry.add(prefix + "virtual-host", address::virtualHost);
                registry.add(prefix + "username", address::username);
                registry.add(prefix + "password", address::password);
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

    @Autowired
    private MarketingBrokerProperties marketing;

    @Autowired
    private Environment environment;

    @Autowired
    private InboxMessageRepository inbox;

    @Autowired
    @MarketingBroker
    private TestQueues marketingQueues;

    @BeforeEach
    void orderingDeclaresItsExchangeAndQueue() {
        RabbitAdmin admin = new RabbitAdmin((ConnectionFactory) marketingConnectionFactory);
        DirectExchange exchange = new DirectExchange("rvl.exchange");
        Queue queue = new Queue(FIXES_QUEUE);
        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("rvl-contract-fixed"));
        admin.purgeQueue(FIXES_QUEUE);
        inbox.deleteAll();
        marketingQueues.purge(ORDER_EVENTS_QUEUE);
    }

    @Test
    void marketingConnection_isTheThirdBrokerInHealthAndConnectionMetrics() {
        Health health = ((HealthIndicator) healthContributors.getContributor("marketingBroker")).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("broker", "marketing")
                .containsEntry("virtualHost", expectedVirtualHost());
        assertThat(marketing.connection().virtualHost()).isEqualTo(expectedVirtualHost());
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

    @Test
    void orderEventQueue_isConsumedByTheLeaderOnTheMarketingBroker() {
        Awaitility.await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(marketingQueues.consumers(ORDER_EVENTS_QUEUE)).isEqualTo(1));
    }

    @Test
    void orderEventFromTheProductQueue_landsInTheInboxJournalOnceAsAPaymentOrderEventAndLeavesTheQueue() {
        String event = "{\"eventId\":\"" + EVENT_ID + "\",\"eventType\":\"paid\",\"orderId\":"
                + "\"0196f0a2-5b1c-7d3e-9f40-1a2b3c4d5e01\",\"orderDetails\":[{\"product\":\"rvl\","
                + "\"documentId\":\"" + DOCUMENT_ID + "\",\"documentPremium\":34000.00}]}";

        publishOrderEvent(event);
        publishOrderEvent(event);

        Awaitility.await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(inbox.existsByMessageId(UUID.fromString(EVENT_ID))).isTrue());
        Awaitility.await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(marketingQueues.depth(ORDER_EVENTS_QUEUE)).isZero());
        assertThat(inbox.findAll()).singleElement().satisfies(stored -> {
            assertThat(stored.receipt()).isEqualTo(new InboxReceipt(UUID.fromString(EVENT_ID),
                    UUID.fromString(DOCUMENT_ID), "ordering.order.events", "PaymentOrderEvent", 0));
            assertThat(stored.status()).isEqualTo(InboxStatus.NEW);
            assertThat(stored.payload()).get().asString().contains(EVENT_ID, DOCUMENT_ID, "paid");
        });
    }

    @Test
    void orderEventWithoutIdentifier_leavesTheQueueLostAndCountedWithoutAJournalEntry() {
        double lostBefore = lostEnvelopes();

        publishOrderEvent("{\"eventType\":\"paid\",\"orderId\":\"0196f0a2-5b1c-7d3e-9f40-1a2b3c4d5e01\"}");

        Awaitility.await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(lostEnvelopes()).isEqualTo(lostBefore + 1));
        Awaitility.await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(marketingQueues.depth(ORDER_EVENTS_QUEUE)).isZero());
        assertThat(inbox.count()).isZero();
    }

    private double lostEnvelopes() {
        return Optional.ofNullable(meters.find("contracts.inbox.lost").tag("reason", "unusable_envelope").counter())
                .map(Counter::count)
                .orElse(0.0);
    }

    private void publishOrderEvent(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        marketingTemplate.send("", ORDER_EVENTS_QUEUE,
                MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8)).andProperties(properties).build());
    }

    private String expectedVirtualHost() {
        String externalBroker = environment.getProperty(TestBroker.URL_PROPERTY, "");
        return externalBroker.isEmpty() ? MARKETING_VHOST : TestBroker.Address.parse(externalBroker).virtualHost();
    }
}
