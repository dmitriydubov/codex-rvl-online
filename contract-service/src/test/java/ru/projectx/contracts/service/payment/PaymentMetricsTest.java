package ru.projectx.contracts.service.payment;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PaymentMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final PaymentMetrics metrics = new PaymentMetrics(registry);

    private double count(String name) {
        return registry.get(name).counter().count();
    }

    @Test
    void expiredTerms_areCountedUnderTheirOwnName() {
        metrics.countTermExpired();
        metrics.countTermExpired();

        assertThat(count("contracts.payment.term.expired")).isEqualTo(2);
        assertThat(registry.getMeters()).hasSize(1);
    }

    @Test
    void paymentErrors_areCountedUnderTheirOwnName() {
        metrics.countPaymentError();

        assertThat(count("contracts.payment.errors")).isEqualTo(1);
        assertThat(registry.getMeters()).hasSize(1);
    }

    @Test
    void paymentsAfterCancellation_areCountedUnderTheirOwnName() {
        metrics.countPaidAfterCancellation();

        assertThat(count("contracts.km.contracts.paid.after.cancellation")).isEqualTo(1);
        assertThat(registry.getMeters()).hasSize(1);
    }

    @Test
    void stoppedPaidContracts_areCountedUnderTheirOwnName() {
        metrics.countPaidCancelled();

        assertThat(count("contracts.km.contracts.paid.cancelled")).isEqualTo(1);
        assertThat(registry.getMeters()).hasSize(1);
    }

    @Test
    void counters_carryDescriptionsForTheOperator() {
        metrics.countTermExpired();
        metrics.countIgnored(IgnoredEvent.OUT_OF_ORDER);
        metrics.countAlert(PaymentAlert.UNEXPECTED_STATE);

        assertThat(registry.get("contracts.payment.term.expired").counter().getId().getDescription())
                .isEqualTo("договоры КМ, прекращённые до счёта, потому что срок оплаты истёк");
        assertThat(registry.get("contracts.payment.events.ignored").counter().getId().getDescription())
                .isEqualTo("события сервиса управления заказами, не применённые к заказу, по причине");
        assertThat(registry.get("contracts.payment.reconciliation.alerts").counter().getId().getDescription())
                .isEqualTo("заказы на оплату, по которым нужен разбор дежурного, по причине");
    }

    @ParameterizedTest
    @CsvSource({
            "UNKNOWN_ORDER, unknown_order",
            "DOCUMENT_MISMATCH, document_mismatch",
            "AMOUNT_MISMATCH, amount_mismatch",
            "FOREIGN_DOCUMENT, foreign_document",
            "OUT_OF_ORDER, out_of_order",
            "NOT_APPLICABLE, not_applicable"})
    void ignoredEvents_areCountedByReason(IgnoredEvent reason, String tag) {
        metrics.countIgnored(reason);
        metrics.countIgnored(reason);

        assertThat(reason.tag()).isEqualTo(tag);
        assertThat(registry.get("contracts.payment.events.ignored").tag("reason", tag).counter().count())
                .isEqualTo(2);
        assertThat(registry.getMeters()).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({
            "DOCUMENT_NOT_FIXED, document_not_fixed",
            "CHECKOUT_REPLY_LOST, checkout_reply_lost",
            "PAYMENT_ERROR_OVERDUE, payment_error_overdue",
            "UNEXPECTED_STATE, unexpected_state",
            "STATE_UNAVAILABLE, state_unavailable",
            "UNEXPECTED_CANCEL, unexpected_cancel"})
    void alerts_areCountedByReason(PaymentAlert reason, String tag) {
        metrics.countAlert(reason);

        assertThat(reason.tag()).isEqualTo(tag);
        assertThat(registry.get("contracts.payment.reconciliation.alerts").tag("reason", tag).counter().count())
                .isEqualTo(1);
        assertThat(registry.getMeters()).hasSize(1);
    }

    @Test
    void reasons_areTheClosedListsOfTheMetricLabels() {
        assertThat(IgnoredEvent.values()).hasSize(6);
        assertThat(PaymentAlert.values()).hasSize(6);
    }
}
