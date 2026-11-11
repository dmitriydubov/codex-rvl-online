package ru.projectx.contracts.config.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PaymentOrderPassesPropertiesTest {

    private static final String PREFIX = "projectx.payment-orders.";
    private static final String[] COMPLETE = {
        "projectx.payment.enabled=true",
        PREFIX + "creation-interval=10s", PREFIX + "cancellation-interval=1m", PREFIX + "batch-size=100",
        PREFIX + "awaiting-interval=2m", PREFIX + "awaiting-threshold=1h",
        PREFIX + "invoices.checkout-interval=15s", PREFIX + "invoices.payment-term=72h",
        PREFIX + "invoices.min-payment-term=1h", PREFIX + "invoices.awaiting-threshold=30m",
        PREFIX + "reconciliation.interval=3m", PREFIX + "reconciliation.threshold=15m",
        PREFIX + "reconciliation.payment-grace=45m", PREFIX + "reconciliation.fix-repeats=2",
        PREFIX + "reconciliation.summary-interval=4m"};

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withUserConfiguration(Passes.class);

    @Test
    void completeConfiguration_bindsThePassesOfOrdersInvoicesAndReconciliation() {
        contexts.withPropertyValues(COMPLETE).run(context -> {
            PaymentOrderPassesProperties passes = context.getBean(PaymentOrderPassesProperties.class);

            assertThat(passes.creationInterval()).isEqualTo(Duration.ofSeconds(10));
            assertThat(passes.cancellationInterval()).isEqualTo(Duration.ofMinutes(1));
            assertThat(passes.batchSize()).isEqualTo(100);
            assertThat(passes.awaitingInterval()).isEqualTo(Duration.ofMinutes(2));
            assertThat(passes.awaitingThreshold()).isEqualTo(Duration.ofHours(1));
            assertThat(passes.invoices()).isEqualTo(new PaymentOrderPassesProperties.Invoices(Duration.ofSeconds(15),
                    Duration.ofHours(72), Duration.ofHours(1), Duration.ofMinutes(30)));
            assertThat(passes.reconciliation()).isEqualTo(new PaymentOrderPassesProperties.Reconciliation(
                    Duration.ofMinutes(3), Duration.ofMinutes(15), Duration.ofMinutes(45), 2, Duration.ofMinutes(4)));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"invoices.checkout-interval=0s", "invoices.payment-term=0s",
        "invoices.min-payment-term=-1s", "invoices.awaiting-threshold=0s", "reconciliation.interval=0s",
        "reconciliation.threshold=0s", "reconciliation.payment-grace=-1s", "reconciliation.fix-repeats=-1",
        "reconciliation.summary-interval=0s"})
    void valueBelowItsLimit_failsTheStart(String setting) {
        contexts.withPropertyValues(COMPLETE).withPropertyValues(PREFIX + setting)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invoices.checkout-interval=1ms", "invoices.payment-term=1ms",
        "invoices.min-payment-term=0s", "invoices.awaiting-threshold=1ms", "reconciliation.interval=1ms",
        "reconciliation.threshold=1ms", "reconciliation.payment-grace=0s", "reconciliation.fix-repeats=0",
        "reconciliation.summary-interval=1ms"})
    void smallestAllowedValue_isAccepted(String setting) {
        contexts.withPropertyValues(COMPLETE).withPropertyValues(PREFIX + setting)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invoices.checkout-interval", "invoices.payment-term", "invoices.min-payment-term",
        "invoices.awaiting-threshold", "reconciliation.interval", "reconciliation.threshold",
        "reconciliation.payment-grace", "reconciliation.summary-interval"})
    void absentDuration_failsTheStart(String name) {
        contexts.withPropertyValues(without(PREFIX + name + "=")).run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invoices.", "reconciliation."})
    void absentSection_failsTheStart(String section) {
        contexts.withPropertyValues(without(PREFIX + section)).run(context -> assertThat(context).hasFailed());
    }

    private static String[] without(String prefix) {
        return Arrays.stream(COMPLETE).filter(value -> !value.startsWith(prefix)).toArray(String[]::new);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PaymentOrderPassesProperties.class)
    static class Passes {
    }
}
