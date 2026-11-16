package ru.projectx.contracts.service.payment;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.contracts.service.payment.invoice.InvoiceRecording;
import ru.projectx.contracts.service.payment.order.PaymentOrderCreation;
import ru.projectx.contracts.service.payment.reconciliation.PaymentReconciliation;
import ru.projectx.contracts.support.FixedClockConfig;

@PaymentRaceTest
class CheckoutReplyLossIT {

    @Autowired
    private PaymentRaceStand stand;

    @Autowired
    private ScriptedOrdering ordering;

    @Autowired
    private PaymentOrderCreation creation;

    @Autowired
    private InvoiceRecording invoices;

    @Autowired
    private PaymentReconciliation reconciliation;

    @Autowired
    private MeterRegistry meters;

    private final UUID request = UUID.randomUUID();
    private UUID contractId;

    @BeforeEach
    void contractWhoseCheckoutGotNoReply() {
        ordering.reset();
        stand.awaitLeadership();
        contractId = stand.acceptedContractWithOrder(request);
        creation.createDue();
        assertThat(stand.orderingEvent(contractId, "fixed", FixedClockConfig.NOW)).isEmpty();
        assertThat(invoices.start(contractId)).isPresent();
        ordering.orderIs((String) stand.order(contractId).get("external_order_id"),
                new OrderingOrder(OrderingStatuses.PENDING_PAYMENT, List.of()));
    }

    @AfterEach
    void removeRecords() {
        stand.remove(request);
    }

    @Test
    void lostReplyOfInvoiceThatTheEventIssued_isReportedByTheFirstReconciliationAndNeverAgain() {
        double alertsBefore = replyLostAlerts();
        try (PaymentErrorLog log = new PaymentErrorLog()) {
            assertThat(stand.orderingEvent(contractId, "pendingPayment", FixedClockConfig.NOW)).isEmpty();

            assertThat(log.errors()).isEmpty();
        }
        Map<String, Object> issuedByTheEvent = stand.invoice(contractId);
        assertThat(issuedByTheEvent.get("state")).isEqualTo("ISSUED");
        assertThat(issuedByTheEvent.get("payment_url")).isNull();
        assertThat(issuedByTheEvent.get("reply_lost_reported")).isEqualTo(false);
        assertThat(replyLostAlerts()).isEqualTo(alertsBefore);

        stand.makeInvoiceStale(contractId);
        reconciliation.reconcileDue();

        assertThat(replyLostAlerts()).isEqualTo(alertsBefore + 1);
        assertThat(stand.invoice(contractId).get("reply_lost_reported")).isEqualTo(true);

        stand.makeInvoiceStale(contractId);
        reconciliation.reconcileDue();

        assertThat(replyLostAlerts()).isEqualTo(alertsBefore + 1);
        assertThat(ordering.stateRequests()).isEqualTo(2);
        Map<String, Object> invoice = stand.invoice(contractId);
        assertThat(invoice.get("state")).isEqualTo("ISSUED");
        assertThat(invoice.get("payment_url")).isNull();
        assertThat(stand.contract(contractId).get("status")).isEqualTo("INVOICE_ISSUED");
        assertThat(stand.order(contractId).get("payer_email")).isNull();
    }

    @Test
    void checkoutStillInFlight_isNotReportedUntilItsMarkIsOlderThanTheReconciliationThreshold() {
        double alertsBefore = replyLostAlerts();

        stand.makeInvoiceStale(contractId);
        reconciliation.reconcileDue();

        assertThat(replyLostAlerts()).isEqualTo(alertsBefore);
        assertThat(stand.invoice(contractId).get("state")).isEqualTo("CHECKOUT");

        stand.makeCheckoutAbandoned(contractId);
        reconciliation.reconcileDue();

        assertThat(replyLostAlerts()).isEqualTo(alertsBefore + 1);
        Map<String, Object> invoice = stand.invoice(contractId);
        assertThat(invoice.get("state")).isEqualTo("ISSUED");
        assertThat(invoice.get("reply_lost_reported")).isEqualTo(true);
    }

    @Test
    void lostReplyOfInvoiceWhosePaymentFailed_isReportedOnceWhileOrderingAwaitsThePayment() {
        double alertsBefore = replyLostAlerts();
        invoiceWithoutLinkWhosePaymentFailed();
        assertThat(replyLostAlerts()).isEqualTo(alertsBefore);

        stand.makeInvoiceStale(contractId);
        reconciliation.reconcileDue();

        assertThat(replyLostAlerts()).isEqualTo(alertsBefore + 1);
        assertThat(stand.invoice(contractId).get("reply_lost_reported")).isEqualTo(true);

        stand.makeInvoiceStale(contractId);
        reconciliation.reconcileDue();

        assertThat(replyLostAlerts()).isEqualTo(alertsBefore + 1);
        Map<String, Object> invoice = stand.invoice(contractId);
        assertThat(invoice.get("state")).isEqualTo("PAYMENT_ERROR");
        assertThat(invoice.get("payment_url")).isNull();
        assertThat(stand.contract(contractId).get("status")).isEqualTo("INVOICE_ISSUED");
    }

    @Test
    void lostReplyOfInvoiceWhosePaymentFailed_isReportedOnceWhileOrderingReportsThePaymentError() {
        double alertsBefore = replyLostAlerts();
        invoiceWithoutLinkWhosePaymentFailed();
        ordering.orderIs((String) stand.order(contractId).get("external_order_id"),
                new OrderingOrder(OrderingStatuses.PAYMENT_ERROR, List.of()));

        stand.makeInvoiceStale(contractId);
        reconciliation.reconcileDue();
        stand.makeInvoiceStale(contractId);
        reconciliation.reconcileDue();

        assertThat(replyLostAlerts()).isEqualTo(alertsBefore + 1);
        Map<String, Object> invoice = stand.invoice(contractId);
        assertThat(invoice.get("state")).isEqualTo("PAYMENT_ERROR");
        assertThat(invoice.get("payment_url")).isNull();
        assertThat(invoice.get("reply_lost_reported")).isEqualTo(true);
    }

    private void invoiceWithoutLinkWhosePaymentFailed() {
        assertThat(stand.orderingEvent(contractId, "pendingPayment", FixedClockConfig.NOW)).isEmpty();
        assertThat(stand.orderingEvent(contractId, "paymentError", FixedClockConfig.NOW)).isEmpty();
        Map<String, Object> failed = stand.invoice(contractId);
        assertThat(failed.get("state")).isEqualTo("PAYMENT_ERROR");
        assertThat(failed.get("payment_url")).isNull();
        assertThat(failed.get("reply_lost_reported")).isEqualTo(false);
    }

    private double replyLostAlerts() {
        return Optional.ofNullable(meters.find("contracts.payment.reconciliation.alerts")
                .tag("reason", "checkout_reply_lost").counter()).map(Counter::count).orElse(0.0);
    }
}
