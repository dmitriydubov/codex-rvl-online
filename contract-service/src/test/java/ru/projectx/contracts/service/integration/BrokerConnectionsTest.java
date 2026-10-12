package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.core.env.Environment;
import ru.projectx.contracts.support.AmqpIntegrationTest;

@AmqpIntegrationTest
class BrokerConnectionsTest {

    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration RECEIVE_TIMEOUT = Duration.ofSeconds(10);
    private static final int REPLY_CODE_NO_ROUTE = 312;

    @Autowired
    @InternalBroker
    private CachingConnectionFactory internalConnectionFactory;

    @Autowired
    @OneCBroker
    private CachingConnectionFactory onecConnectionFactory;

    @Autowired
    @InternalBroker
    private RabbitTemplate internalTemplate;

    @Autowired
    @OneCBroker
    private RabbitTemplate onecTemplate;

    @Autowired
    private HealthContributorRegistry healthContributors;

    @Autowired
    private MeterRegistry meters;

    @Autowired
    private Environment environment;

    @Test
    void connections_openIntoTheirOwnVirtualHostsUnderTheServiceName() {
        Connection internal = internalConnectionFactory.createConnection();
        Connection onec = onecConnectionFactory.createConnection();

        assertThat(internal.isOpen()).isTrue();
        assertThat(onec.isOpen()).isTrue();
        assertThat(internal.getDelegate()).isNotSameAs(onec.getDelegate());
        assertThat(internal.getDelegate().getClientProvidedName()).isEqualTo("contract-service:internal");
        assertThat(onec.getDelegate().getClientProvidedName()).isEqualTo("contract-service:onec");
        assertThat(internalConnectionFactory.getVirtualHost()).isEqualTo(configuredVirtualHost("internal"));
        assertThat(onecConnectionFactory.getVirtualHost()).isEqualTo(configuredVirtualHost("onec"));
    }

    @Test
    void publishToInternalBroker_isConfirmedAndLandsInTheQueue()
            throws ExecutionException, InterruptedException, TimeoutException {
        assertConfirmedDelivery(internalConnectionFactory, internalTemplate, "internal-confirm-test");
    }

    @Test
    void publishToOneCBroker_isConfirmedAndLandsInTheQueue()
            throws ExecutionException, InterruptedException, TimeoutException {
        assertConfirmedDelivery(onecConnectionFactory, onecTemplate, "onec-confirm-test");
    }

    @Test
    void publishToMissingQueue_isConfirmedButReturned()
            throws ExecutionException, InterruptedException, TimeoutException {
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());

        internalTemplate.convertAndSend("", "queue-that-nobody-declared", "тело сообщения", correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

        assertThat(confirm.ack()).isTrue();
        assertThat(correlation.getReturned()).isNotNull();
        assertThat(correlation.getReturned().getReplyCode()).isEqualTo(REPLY_CODE_NO_ROUTE);
    }

    @Test
    void health_showsEachConnectionUpUnderItsOwnName() {
        assertThat(healthOf("internalBroker").getStatus()).isEqualTo(Status.UP);
        assertThat(healthOf("onecBroker").getStatus()).isEqualTo(Status.UP);
        assertThat(healthOf("onecBroker").getDetails())
                .containsEntry("broker", "onec")
                .containsEntry("virtualHost", configuredVirtualHost("onec"))
                .doesNotContainKeys("username", "password");
    }

    @Test
    void connectionMetrics_areTaggedWithTheBroker() {
        assertThat(meters.find("rabbitmq.connections").tag("name", "internalBroker").gauge()).isNotNull();
        assertThat(meters.find("rabbitmq.connections").tag("name", "onecBroker").gauge()).isNotNull();
    }

    private String configuredVirtualHost(String broker) {
        return environment.getRequiredProperty("projectx.brokers." + broker + ".connection.virtual-host");
    }

    private Health healthOf(String component) {
        return ((HealthIndicator) healthContributors.getContributor(component)).health();
    }

    private static void assertConfirmedDelivery(CachingConnectionFactory connectionFactory, RabbitTemplate template,
            String queue) throws ExecutionException, InterruptedException, TimeoutException {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.declareQueue(QueueBuilder.durable(queue).build());
        try {
            CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
            template.convertAndSend("", queue, "тело сообщения", correlation);

            CorrelationData.Confirm confirm = correlation.getFuture().get(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            Message delivered = template.receive(queue, RECEIVE_TIMEOUT.toMillis());

            assertThat(confirm.ack()).isTrue();
            assertThat(correlation.getReturned()).isNull();
            assertThat(delivered).isNotNull();
            assertThat(delivered.getBody()).asString().isEqualTo("тело сообщения");
        }
        finally {
            admin.deleteQueue(queue);
        }
    }
}
