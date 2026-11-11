package ru.projectx.contracts.config.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class PaymentOrderDefaultsTest {

    private static PropertySource<?> application;

    @BeforeAll
    static void loadApplicationYaml() throws IOException {
        application = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml")).getFirst();
    }

    @Test
    void invoiceDefaults_checkOutEveryTenSecondsGiveThreeDaysToPayAndNeedAtLeastAnHour() {
        assertThat(setting("projectx.payment-orders.invoices.checkout-interval"))
                .isEqualTo("${PAYMENT_INVOICE_CHECKOUT_INTERVAL:10s}");
        assertThat(setting("projectx.payment-orders.invoices.payment-term")).isEqualTo("${PAYMENT_TERM:72h}");
        assertThat(setting("projectx.payment-orders.invoices.min-payment-term")).isEqualTo("${PAYMENT_MIN_TERM:1h}");
        assertThat(setting("projectx.payment-orders.invoices.awaiting-threshold"))
                .isEqualTo("${PAYMENT_INVOICE_AWAITING_THRESHOLD:30m}");
    }

    @Test
    void reconciliationDefaults_runEveryMinuteOverInvoicesSilentForFifteenMinutesWithAnHourOfGraceAndTwoRepeats() {
        assertThat(setting("projectx.payment-orders.reconciliation.interval"))
                .isEqualTo("${PAYMENT_RECONCILIATION_INTERVAL:1m}");
        assertThat(setting("projectx.payment-orders.reconciliation.threshold"))
                .isEqualTo("${PAYMENT_RECONCILIATION_THRESHOLD:15m}");
        assertThat(setting("projectx.payment-orders.reconciliation.payment-grace"))
                .isEqualTo("${PAYMENT_OVERDUE_GRACE:1h}");
        assertThat(setting("projectx.payment-orders.reconciliation.fix-repeats"))
                .isEqualTo("${PAYMENT_DOCUMENT_FIX_REPEATS:2}");
        assertThat(setting("projectx.payment-orders.reconciliation.summary-interval"))
                .isEqualTo("${PAYMENT_SUMMARY_INTERVAL:1m}");
    }

    @Test
    void marketingBrokerDefaults_listenToTheOrderEventQueueNamedByTheEnvironment() {
        assertThat(setting("projectx.brokers.marketing.listener-auto-startup"))
                .isEqualTo("${RABBITMQ_MARKETING_LISTENER_AUTO_STARTUP:true}");
        assertThat(setting("projectx.brokers.marketing.order-events.queue"))
                .isEqualTo("${MARKETING_ORDER_EVENTS_QUEUE:}");
    }

    private static String setting(String name) {
        return String.valueOf(application.getProperty(name));
    }
}
