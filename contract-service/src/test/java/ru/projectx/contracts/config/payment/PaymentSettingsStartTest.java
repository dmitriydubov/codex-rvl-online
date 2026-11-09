package ru.projectx.contracts.config.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import ru.projectx.contracts.config.broker.MarketingBrokerProperties;

class PaymentSettingsStartTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PaymentSettingsScan.class)
            .withPropertyValues(otherBrokers());

    private static String[] otherBrokers() {
        return new String[] {
                "projectx.brokers.internal.connection.host=internal.test",
                "projectx.brokers.internal.connection.port=5672",
                "projectx.brokers.internal.connection.virtual-host=/",
                "projectx.brokers.internal.connection.username=contracts",
                "projectx.brokers.internal.connection.password=no-broker",
                "projectx.brokers.internal.connection.connection-timeout=10s",
                "projectx.brokers.internal.queues.task-changes=tasks",
                "projectx.brokers.internal.queues.status-changes=statuses",
                "projectx.brokers.onec.connection.host=onec.test",
                "projectx.brokers.onec.connection.port=5672",
                "projectx.brokers.onec.connection.virtual-host=/",
                "projectx.brokers.onec.connection.username=contracts",
                "projectx.brokers.onec.connection.password=no-broker",
                "projectx.brokers.onec.connection.connection-timeout=10s",
                "projectx.brokers.onec.queues.km-contract-submissions=km-submissions",
                "projectx.brokers.onec.queues.km-contract-registrations=km-registrations",
                "projectx.brokers.onec.queues.lm-policy-requests=lm-requests",
                "projectx.brokers.onec.queues.lm-policy-issues=lm-issues"};
    }

    @Configuration(proxyBeanMethods = false)
    @ConfigurationPropertiesScan(basePackageClasses = {PaymentProperties.class, MarketingBrokerProperties.class})
    static class PaymentSettingsScan {
    }

    @Test
    void disabledPayment_needsNoOrderingKeycloakOrMarketingSettings() {
        runner.withPropertyValues("projectx.payment.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OrderingProperties.class);
            assertThat(context).doesNotHaveBean(PaymentOrderPassesProperties.class);
            assertThat(context).doesNotHaveBean(MarketingBrokerProperties.class);
        });
    }

    @Test
    void enabledPaymentWithoutOrderingSettings_doesNotStart() {
        runner.withPropertyValues("projectx.payment.enabled=true").run(context ->
                assertThat(context).hasFailed());
    }

    @Test
    void paymentSwitchLeftEmpty_doesNotStart() {
        runner.withPropertyValues("projectx.payment.enabled=").run(context -> assertThat(context).hasFailed());
    }
}
