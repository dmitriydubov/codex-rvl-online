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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.message.task.ContractTask;
import ru.projectx.contracts.message.task.ContractTaskCancellation;
import ru.projectx.contracts.service.cancellation.TaskCancellationService;
import ru.projectx.contracts.service.issuance.IssuanceService;
import ru.projectx.contracts.service.payment.invoice.InvoiceIssuing;
import ru.projectx.contracts.service.payment.order.PaymentOrderCreation;
import ru.projectx.contracts.service.payment.reconciliation.PaymentReconciliation;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.support.FixedClockConfig;
import ru.projectx.contracts.support.Tasks;

@PaymentRaceTest
class PaymentOutcomeRaceIT {

    private static final Instant NOW = FixedClockConfig.NOW;
    private static final Instant CANCELLED_AT = Instant.parse("2026-03-14T09:14:00Z");
    private static final Instant EVENT_AT = Instant.parse("2026-03-14T09:15:03.250Z");

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
    private IssuanceService issuance;

    @Autowired
    private RequestStatusPublication statuses;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MeterRegistry meters;

    private final UUID request = UUID.randomUUID();
    private RowWaits rows;
    private UUID contractId;

    @BeforeEach
    void contractWithIssuedInvoice() {
        rows = new RowWaits(transaction, jdbc);
        ordering.reset();
        stand.awaitLeadership();
        contractId = stand.acceptedContractWithOrder(request);
        creation.createDue();
        assertThat(stand.orderingEvent(contractId, "fixed", NOW)).isEmpty();
        issuing.issueDue();
        assertThat(stand.invoice(contractId).get("state")).isEqualTo("ISSUED");
        assertThat(stand.contract(contractId).get("status")).isEqualTo("INVOICE_ISSUED");
    }

    @AfterEach
    void removeRecords() {
        stand.remove(request);
    }

    @Test
    void paymentWaitingWhileTheCancellationCommits_putsTheContractInErrorForTheRefund() throws Exception {
        double refundsBefore = count("contracts.km.contracts.paid.after.cancellation");

        rows.commitTheFirstOnceTheSecondWaitsForItsRows(this::cancelRequest,
                () -> assertThat(stand.orderingEvent(contractId, "paid", EVENT_AT)).isEmpty());

        Map<String, Object> contract = stand.contract(contractId);
        assertThat(contract.get("status")).isEqualTo("ERROR");
        assertThat(contract.get("error_code")).isEqualTo("KM_CONTRACT_PAID_AFTER_CANCELLATION");
        assertThat(contract.get("paid_at")).isNull();
        Map<String, Object> invoice = stand.invoice(contractId);
        assertThat(invoice.get("state")).isEqualTo("PAID");
        assertThat(moment(invoice.get("paid_at"))).isEqualTo(EVENT_AT);
        assertThat(invoice.get("deferred_cancel_reason")).isNull();
        assertThat(count("contracts.km.contracts.paid.after.cancellation")).isEqualTo(refundsBefore + 1);
    }

    @Test
    void cancellationWaitingWhileThePaymentCommits_cancelsThePaidContractAndLeavesThePaidOrderAlone()
            throws Exception {
        double stoppedBefore = count("contracts.km.contracts.paid.cancelled");

        rows.commitTheFirstOnceTheSecondWaitsForItsRows(
                () -> assertThat(stand.orderingEvent(contractId, "paid", EVENT_AT)).isEmpty(), this::cancelRequest);

        Map<String, Object> contract = stand.contract(contractId);
        assertThat(contract.get("status")).isEqualTo("CANCELLED");
        assertThat(contract.get("cancel_reason")).isEqualTo("INSURED_REFUSED");
        assertThat(moment(contract.get("paid_at"))).isEqualTo(EVENT_AT);
        Map<String, Object> invoice = stand.invoice(contractId);
        assertThat(invoice.get("state")).isEqualTo("PAID");
        assertThat(invoice.get("deferred_cancel_reason")).isNull();
        assertThat(stand.order(contractId).get("state")).isEqualTo("CREATED");
        assertThat(count("contracts.km.contracts.paid.cancelled")).isEqualTo(stoppedBefore + 1);
    }

    @Test
    void overdueWaitingWhileTheCancellationCommits_appliesTheDeferredCancellation() throws Exception {
        rows.commitTheFirstOnceTheSecondWaitsForItsRows(this::cancelRequest,
                () -> assertThat(stand.orderingEvent(contractId, "paymentOverdue", EVENT_AT)).isEmpty());

        Map<String, Object> contract = stand.contract(contractId);
        assertThat(contract.get("status")).isEqualTo("CANCELLED");
        assertThat(contract.get("cancel_reason")).isEqualTo("INSURED_REFUSED");
        assertThat(moment(contract.get("cancelled_at"))).isEqualTo(CANCELLED_AT);
        Map<String, Object> invoice = stand.invoice(contractId);
        assertThat(invoice.get("state")).isEqualTo("OVERDUE");
        assertThat(invoice.get("deferred_cancel_reason")).isNull();
    }

    @Test
    void cancellationWaitingWhileTheOverdueCommits_leavesTheContractCancelledAsExpired() throws Exception {
        rows.commitTheFirstOnceTheSecondWaitsForItsRows(
                () -> assertThat(stand.orderingEvent(contractId, "paymentOverdue", EVENT_AT)).isEmpty(),
                this::cancelRequest);

        Map<String, Object> contract = stand.contract(contractId);
        assertThat(contract.get("status")).isEqualTo("CANCELLED");
        assertThat(contract.get("cancel_reason")).isEqualTo("EXPIRED");
        assertThat(moment(contract.get("cancelled_at"))).isEqualTo(EVENT_AT);
        Map<String, Object> invoice = stand.invoice(contractId);
        assertThat(invoice.get("state")).isEqualTo("OVERDUE");
        assertThat(invoice.get("deferred_cancel_reason")).isNull();
    }

    @Test
    void overdueWaitingWhileThePaymentCommits_isSkippedAndThePaidContractStays() throws Exception {
        rows.commitTheFirstOnceTheSecondWaitsForItsRows(
                () -> assertThat(stand.orderingEvent(contractId, "paid", EVENT_AT)).isEmpty(),
                () -> assertThat(stand.orderingEvent(contractId, "paymentOverdue", EVENT_AT.plusSeconds(1)))
                        .contains("событие paymentOverdue не по порядку: заказ уже оплачен"));

        Map<String, Object> contract = stand.contract(contractId);
        assertThat(contract.get("status")).isEqualTo("PAID");
        assertThat(contract.get("cancel_reason")).isNull();
        assertThat(contract.get("entity_version")).isEqualTo(2);
        assertThat(stand.invoice(contractId).get("state")).isEqualTo("PAID");
    }

    @Test
    void paymentWaitingWhileTheOverdueCommits_keepsTheContractCancelledAndAsksForTheRefund() throws Exception {
        double refundsBefore = count("contracts.km.contracts.paid.after.cancellation");

        rows.commitTheFirstOnceTheSecondWaitsForItsRows(
                () -> assertThat(stand.orderingEvent(contractId, "paymentOverdue", EVENT_AT)).isEmpty(),
                () -> assertThat(stand.orderingEvent(contractId, "paid", EVENT_AT.plusSeconds(1))).isEmpty());

        Map<String, Object> contract = stand.contract(contractId);
        assertThat(contract.get("status")).isEqualTo("CANCELLED");
        assertThat(contract.get("cancel_reason")).isEqualTo("EXPIRED");
        assertThat(contract.get("paid_at")).isNull();
        Map<String, Object> invoice = stand.invoice(contractId);
        assertThat(invoice.get("state")).isEqualTo("PAID");
        assertThat(moment(invoice.get("paid_at"))).isEqualTo(EVENT_AT.plusSeconds(1));
        assertThat(count("contracts.km.contracts.paid.after.cancellation")).isEqualTo(refundsBefore + 1);
    }

    @Test
    void reconciliationWaitingWhileThePaymentEventCommits_appliesThePaymentOnce() throws Exception {
        stand.makeInvoiceStale(contractId);
        ordering.orderIs(orderId(), new OrderingOrder(OrderingStatuses.PAID,
                List.of(new OrderingEvent("paid", EVENT_AT.plusSeconds(1)))));
        long messagesBefore = stand.statusMessages(request);
        double alertsBefore = unexpectedStateAlerts();

        try (PaymentErrorLog log = new PaymentErrorLog()) {
            rows.commitTheFirstOnceTheSecondWaitsForItsRows(
                    () -> assertThat(stand.orderingEvent(contractId, "paid", EVENT_AT)).isEmpty(),
                    reconciliation::reconcileDue);

            assertThat(log.errors()).isEmpty();
        }

        assertThat(unexpectedStateAlerts()).isEqualTo(alertsBefore);
        Map<String, Object> contract = stand.contract(contractId);
        assertThat(contract.get("status")).isEqualTo("PAID");
        assertThat(moment(contract.get("paid_at"))).isEqualTo(EVENT_AT);
        assertThat(contract.get("entity_version")).isEqualTo(2);
        assertThat(moment(stand.invoice(contractId).get("paid_at"))).isEqualTo(EVENT_AT);
        assertThat(stand.statusMessages(request)).isEqualTo(messagesBefore);
        assertThat(ordering.stateRequests()).isEqualTo(1);
    }

    @Test
    void overdueWaitingWhileTheNewTaskLiftsTheDeferredCancellation_findsOneContractAndNoPolicy() throws Exception {
        cancelRequest();
        assertThat(stand.invoice(contractId).get("deferred_cancel_reason")).isEqualTo("INSURED_REFUSED");

        rows.commitTheFirstOnceTheSecondWaitsForItsRows(this::applyTheSameTaskAgain,
                () -> assertThat(stand.orderingEvent(contractId, "paymentOverdue", EVENT_AT)).isEmpty());

        assertThat(stand.contractsOf(request)).isEqualTo(1);
        assertThat(stand.policiesOf(request)).isZero();
        Map<String, Object> contract = stand.contract(contractId);
        assertThat(contract.get("status")).isEqualTo("CANCELLED");
        assertThat(contract.get("cancel_reason")).isEqualTo("EXPIRED");
        Map<String, Object> invoice = stand.invoice(contractId);
        assertThat(invoice.get("state")).isEqualTo("OVERDUE");
        assertThat(invoice.get("deferred_cancel_reason")).isNull();
    }

    @Test
    void newTaskWhileTheContractWaitsForOverdue_resumesTheSameContractWithoutSecondContractOrPolicy() {
        cancelRequest();

        applyTheSameTaskAgain();

        assertThat(stand.contractsOf(request)).isEqualTo(1);
        assertThat(stand.policiesOf(request)).isZero();
        assertThat(stand.contract(contractId).get("status")).isEqualTo("INVOICE_ISSUED");
        Map<String, Object> invoice = stand.invoice(contractId);
        assertThat(invoice.get("state")).isEqualTo("ISSUED");
        assertThat(invoice.get("deferred_cancel_reason")).isNull();
        assertThat(invoice.get("deferred_cancelled_at")).isNull();
    }

    private void cancelRequest() {
        transaction.executeWithoutResult(status -> {
            cancellation.apply(new ContractTaskCancellation(request, CANCELLED_AT, "INSURED_REFUSED", null), NOW);
            statuses.afterCancellation(request, NOW);
        });
    }

    private void applyTheSameTaskAgain() {
        ContractTask task = new ContractTask(2, request, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY, Tasks.FROM,
                Tasks.TO, List.of(Tasks.insured("0.00"), Tasks.spouse("34000.00")));
        transaction.executeWithoutResult(status -> {
            IssuanceSnapshot before = statuses.watch(request);
            statuses.afterTask(before, request, issuance.apply(task, NOW), NOW);
        });
    }

    private String orderId() {
        return (String) stand.order(contractId).get("external_order_id");
    }

    private double unexpectedStateAlerts() {
        return Optional.ofNullable(meters.find("contracts.payment.reconciliation.alerts")
                .tag("reason", "unexpected_state").counter()).map(Counter::count).orElse(0.0);
    }

    private double count(String counter) {
        return Optional.ofNullable(meters.find(counter).counter()).map(Counter::count).orElse(0.0);
    }

    private static Instant moment(Object column) {
        return ((Timestamp) column).toInstant();
    }
}
