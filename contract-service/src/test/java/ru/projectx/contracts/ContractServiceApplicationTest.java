package ru.projectx.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.projectx.contracts.config.broker.MarketingBrokerProperties;
import ru.projectx.contracts.config.payment.OrderingProperties;
import ru.projectx.contracts.service.payment.Ordering;
import ru.projectx.contracts.service.payment.PaymentOrderOpening;
import ru.projectx.contracts.service.payment.cancellation.PaymentOrderCancelling;
import ru.projectx.contracts.service.payment.order.DisabledPaymentOrders;
import ru.projectx.contracts.service.payment.order.PaymentOrderCreation;
import ru.projectx.contracts.support.FixedClockConfig;
import ru.projectx.contracts.support.IntegrationTest;

@IntegrationTest
class ContractServiceApplicationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private HealthContributorRegistry healthContributors;

    @Autowired
    private MeterRegistry meters;

    @Test
    void brokerBeans_existWithoutReachableBroker() {
        Map<String, ConnectionFactory> connectionFactories = context.getBeansOfType(ConnectionFactory.class);
        Map<String, RabbitTemplate> templates = context.getBeansOfType(RabbitTemplate.class);

        assertThat(connectionFactories).containsOnlyKeys("internalBrokerConnectionFactory",
                "onecBrokerConnectionFactory");
        assertThat(templates).containsOnlyKeys("internalBrokerTemplate", "onecBrokerTemplate");
    }

    @Test
    void disabledPayment_startsWithoutOrderingKeycloakAndMarketingBrokerSettings() {
        assertThat(context.getBeansOfType(Ordering.class)).isEmpty();
        assertThat(context.getBeansOfType(OrderingProperties.class)).isEmpty();
        assertThat(context.getBeansOfType(MarketingBrokerProperties.class)).isEmpty();
        assertThat(context.getBeansOfType(PaymentOrderCreation.class)).isEmpty();
        assertThat(context.getBeansOfType(PaymentOrderCancelling.class)).isEmpty();
        assertThat(context.getBean(PaymentOrderOpening.class)).isInstanceOf(DisabledPaymentOrders.class);
        assertThat(meters.get("contracts.payment.disabled").gauge().value()).isEqualTo(1);
    }

    @Test
    void brokerHealth_isSwitchedOffInThisContext() {
        assertThat(healthContributors.getContributor("internalBroker")).isNull();
        assertThat(healthContributors.getContributor("onecBroker")).isNull();
        assertThat(healthContributors.getContributor("rabbit")).isNull();
    }

    @Test
    void migrations_runOnStartup() {
        Integer journals = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'databasechangelog'", Integer.class);

        assertThat(journals).isEqualTo(1);
    }

    @Test
    void clock_isTheStoppedTestClock() {
        assertThat(clock.instant()).isEqualTo(FixedClockConfig.NOW);
    }
}
