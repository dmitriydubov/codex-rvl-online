package ru.projectx.contracts.service.payment;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.message.task.ContractTaskCancellation;
import ru.projectx.contracts.service.cancellation.TaskCancellationService;
import ru.projectx.contracts.service.payment.invoice.InvoiceIssuing;
import ru.projectx.contracts.service.payment.order.PaymentOrderCreation;
import ru.projectx.contracts.service.payment.reconciliation.PaymentReconciliation;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.support.FixedClockConfig;

@PaymentRaceTest
class PaymentInvoiceRaceIT {

    private static final Instant CANCELLED_AT = Instant.parse("2026-03-14T09:14:00Z");
    private static final Instant PAID_AT = Instant.parse("2026-03-14T09:15:03.250Z");

    @Autowired
    private PaymentRaceStand stand;

    @Autowired
    private ScriptedOrdering ordering;

    @Autowired
    private PaymentOrderCreation creation;

    @Autowired
    private InvoiceIssuing issuing;

    @Autowired
    private PaymentReconciliation reconciliation;

    @Autowired
    private TaskCancellationService cancellation;

    @Autowired
    private RequestStatusPublication statuses;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private MeterRegistry meters;

    private final UUID request = UUID.randomUUID();
    private UUID contractId;

    @BeforeEach
    void acceptedContractWhoseOrderIsReadyForCheckout() {
        ordering.reset();
        stand.awaitLeadership();
        contractId = stand.acceptedContractWithOrder(request);
        creation.createDue();
        assertThat(stand.orderingEvent(contractId, "fixed", FixedClockConfig.NOW)).isEmpty();
        assertThat(stand.invoice(contractId).get("state")).isEqualTo("READY");
    }

    @AfterEach
    void removeRecords() {
        stand.returnLeadership();
        stand.remove(request);
    }

    @Test
    void cancellationCommittedWhileOrderingIssuesTheInvoice_isDeferredAndTheCheckoutReplyDoesNotLiftIt() {
        long messagesBefore = stand.statusMessages(request);
        ordering.duringCheckout(this::cancelRequest);

        issuing.issueDue();

        Map<String, Object> invoice = stand.invoice(contractId);
        assertThat(invoice.get("state")).isEqualTo("ISSUED");
        assertThat(invoice.get("payment_url")).isEqualTo(ScriptedOrdering.PAYMENT_URL + "invoice-1");
        assertThat(invoice.get("deferred_cancel_reason")).isEqualTo("INSURED_REFUSED");
        assertThat(moment(invoice.get("deferred_cancelled_at"))).isEqualTo(CANCELLED_AT);
        Map<String, Object> contract = stand.contract(contractId);
        assertThat(contract.get("status")).isEqualTo("INVOICE_ISSUED");
        assertThat(contract.get("cancel_reason")).isNull();
        assertThat(stand.order(contractId).get("state")).isEqualTo("CREATED");
        assertThat(stand.statusMessages(request)).isEqualTo(messagesBefore + 1);
        assertThat(ordering.cancelled()).isEmpty();
    }

    @Test
    void paymentCommittedWhileOrderingIssuesTheInvoice_paysTheContractAndTheCheckoutReplyDoesNotSpoilIt() {
        ordering.duringCheckout(() -> assertThat(stand.orderingEvent(contractId, "paid", PAID_AT)).isEmpty());

        issuing.issueDue();

        Map<String, Object> contract = stand.contract(contractId);
        assertThat(contract.get("status")).isEqualTo("PAID");
        assertThat(moment(contract.get("paid_at"))).isEqualTo(PAID_AT);
        assertThat(contract.get("entity_version")).isEqualTo(2);
        Map<String, Object> invoice = stand.invoice(contractId);
        assertThat(invoice.get("state")).isEqualTo("PAID");
        assertThat(moment(invoice.get("paid_at"))).isEqualTo(PAID_AT);
        assertThat(invoice.get("payment_url")).isNull();
        assertThat(invoice.get("invoice_id")).isNull();
        assertThat(stand.order(contractId).get("payer_email")).isNull();
        assertThat(ordering.checkouts()).isEqualTo(1);
    }

    @Test
    void pendingPaymentCommittedBeforeTheCheckoutReplyIsRecorded_stillGetsTheLinkAndRequestsGetTheReply() {
        long messagesBefore = stand.statusMessages(request);
        double alertsBefore = alerts("checkout_reply_lost");
        ordering.duringCheckout(
                () -> assertThat(stand.orderingEvent(contractId, "pendingPayment", PAID_AT)).isEmpty());

        try (PaymentErrorLog log = new PaymentErrorLog()) {
            issuing.issueDue();

            assertThat(log.errors()).isEmpty();
        }

        assertThat(alerts("checkout_reply_lost")).isEqualTo(alertsBefore);
        Map<String, Object> invoice = stand.invoice(contractId);
        assertThat(invoice.get("state")).isEqualTo("ISSUED");
        assertThat(invoice.get("invoice_id")).isEqualTo("invoice-1");
        assertThat(invoice.get("payment_url")).isEqualTo(ScriptedOrdering.PAYMENT_URL + "invoice-1");
        assertThat(stand.contract(contractId).get("status")).isEqualTo("INVOICE_ISSUED");
        assertThat(stand.order(contractId).get("payer_email")).isNull();
        assertThat(stand.statusMessages(request)).isEqualTo(messagesBefore + 1);
        assertThat(stand.lastStatusMessage(request)).contains("PENDING")
                .contains(ScriptedOrdering.PAYMENT_URL + "invoice-1");
        assertThat(ordering.checkouts()).isEqualTo(1);
    }

    @Test
    void staleReadyInvoiceTakenByThePassAndByReconciliationAtOnce_isCheckedOutOnce() throws Exception {
        stand.makeInvoiceStale(contractId);
        ordering.orderIs(orderId(), new OrderingOrder(OrderingStatuses.READY_TO_PAYMENT, List.of()));
        CountDownLatch passInCheckout = new CountDownLatch(1);
        CountDownLatch reconciled = new CountDownLatch(1);
        AtomicReference<CompletableFuture<Integer>> pass = new AtomicReference<>();
        ordering.duringCheckout(() -> {
            passInCheckout.countDown();
            awaitQuietly(reconciled);
        });
        ordering.duringStateRequest(() -> {
            pass.set(CompletableFuture.supplyAsync(issuing::issueDue));
            awaitQuietly(passInCheckout);
        });

        reconciliation.reconcileDue();
        reconciled.countDown();
        pass.get().get(PaymentRaceStand.WAIT.toSeconds(), TimeUnit.SECONDS);

        assertThat(ordering.checkouts()).isEqualTo(1);
        Map<String, Object> invoice = stand.invoice(contractId);
        assertThat(invoice.get("state")).isEqualTo("ISSUED");
        assertThat(invoice.get("payment_url")).isEqualTo(ScriptedOrdering.PAYMENT_URL + "invoice-1");
        assertThat(stand.contract(contractId).get("status")).isEqualTo("INVOICE_ISSUED");
    }

    @Test
    void leadershipLostBetweenTheCheckoutAndItsRecord_rollsTheRecordBackAndReconciliationAdmitsTheLostReply() {
        double alertsBefore = alerts("checkout_reply_lost");
        ordering.duringCheckout(() -> stand.handLeadershipTo("stand-other-instance"));

        issuing.issueDue();

        Map<String, Object> lost = stand.invoice(contractId);
        assertThat(lost.get("state")).isEqualTo("CHECKOUT");
        assertThat(moment(lost.get("checkout_started_at"))).isEqualTo(FixedClockConfig.NOW);
        assertThat(lost.get("payment_url")).isNull();
        assertThat(stand.contract(contractId).get("status")).isEqualTo("ACCEPTED");
        assertThat(stand.order(contractId).get("payer_email")).isEqualTo(PaymentRaceStand.EMAIL);

        ordering.duringCheckout(() -> { });
        stand.returnLeadership();
        issuing.issueDue();
        assertThat(ordering.checkouts()).isEqualTo(1);

        stand.makeCheckoutAbandoned(contractId);
        reconciliation.reconcileDue();

        Map<String, Object> admitted = stand.invoice(contractId);
        assertThat(admitted.get("state")).isEqualTo("ISSUED");
        assertThat(admitted.get("payment_url")).isNull();
        assertThat(stand.contract(contractId).get("status")).isEqualTo("INVOICE_ISSUED");
        assertThat(stand.order(contractId).get("payer_email")).isNull();
        assertThat(ordering.checkouts()).isEqualTo(1);
        assertThat(alerts("checkout_reply_lost")).isEqualTo(alertsBefore + 1);
    }

    @Test
    void paymentEventLandingBetweenTheStateRequestAndItsRecord_isAppliedOnceAndReconciliationAddsNothing() {
        issuing.issueDue();
        stand.makeInvoiceStale(contractId);
        ordering.orderIs(orderId(), new OrderingOrder(OrderingStatuses.PAID,
                List.of(new OrderingEvent("paid", PAID_AT.plusSeconds(1)))));
        long messagesBefore = stand.statusMessages(request);
        double alertsBefore = alerts("unexpected_state");
        ordering.duringStateRequest(() -> assertThat(stand.orderingEvent(contractId, "paid", PAID_AT)).isEmpty());

        try (PaymentErrorLog log = new PaymentErrorLog()) {
            reconciliation.reconcileDue();

            assertThat(log.errors()).isEmpty();
        }

        Map<String, Object> contract = stand.contract(contractId);
        assertThat(contract.get("status")).isEqualTo("PAID");
        assertThat(moment(contract.get("paid_at"))).isEqualTo(PAID_AT);
        assertThat(contract.get("entity_version")).isEqualTo(2);
        assertThat(moment(stand.invoice(contractId).get("paid_at"))).isEqualTo(PAID_AT);
        assertThat(stand.statusMessages(request)).isEqualTo(messagesBefore);
        assertThat(ordering.stateRequests()).isEqualTo(1);
        assertThat(alerts("unexpected_state")).isEqualTo(alertsBefore);
    }

    @Test
    void leadershipLostBetweenTheStateRequestAndItsRecord_rollsTheOutcomeBackUntilTheRoleReturns() {
        issuing.issueDue();
        stand.makeInvoiceStale(contractId);
        ordering.orderIs(orderId(), new OrderingOrder(OrderingStatuses.PAID,
                List.of(new OrderingEvent("paid", PAID_AT))));
        ordering.duringStateRequest(() -> stand.handLeadershipTo("stand-other-instance"));

        reconciliation.reconcileDue();

        assertThat(stand.invoice(contractId).get("state")).isEqualTo("ISSUED");
        assertThat(stand.contract(contractId).get("status")).isEqualTo("INVOICE_ISSUED");
        assertThat(stand.contract(contractId).get("paid_at")).isNull();

        ordering.duringStateRequest(() -> { });
        stand.returnLeadership();
        reconciliation.reconcileDue();

        Map<String, Object> contract = stand.contract(contractId);
        assertThat(contract.get("status")).isEqualTo("PAID");
        assertThat(moment(contract.get("paid_at"))).isEqualTo(PAID_AT);
        assertThat(stand.invoice(contractId).get("state")).isEqualTo("PAID");
    }

    private void cancelRequest() {
        transaction.executeWithoutResult(status -> {
            cancellation.apply(new ContractTaskCancellation(request, CANCELLED_AT, "INSURED_REFUSED", null),
                    FixedClockConfig.NOW);
            statuses.afterCancellation(request, FixedClockConfig.NOW);
        });
    }

    private String orderId() {
        return (String) stand.order(contractId).get("external_order_id");
    }

    private double alerts(String reason) {
        return Optional.ofNullable(meters.find("contracts.payment.reconciliation.alerts").tag("reason", reason)
                .counter()).map(Counter::count).orElse(0.0);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(PaymentRaceStand.WAIT.toSeconds(), TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static Instant moment(Object column) {
        return ((Timestamp) column).toInstant();
    }
}
