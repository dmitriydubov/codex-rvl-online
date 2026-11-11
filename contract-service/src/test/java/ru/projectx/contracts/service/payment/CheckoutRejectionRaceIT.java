package ru.projectx.contracts.service.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingRejectedException;
import ru.projectx.contracts.message.task.ContractTaskCancellation;
import ru.projectx.contracts.service.cancellation.TaskCancellationService;
import ru.projectx.contracts.service.payment.invoice.InvoiceIssuing;
import ru.projectx.contracts.service.payment.order.PaymentOrderCreation;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.support.FixedClockConfig;

@PaymentRaceTest
class CheckoutRejectionRaceIT {

    private static final Instant CANCELLED_AT = Instant.parse("2026-03-14T09:14:00Z");
    private static final String CODE = "CheckoutValidatorService.valid";

    @Autowired
    private PaymentRaceStand stand;

    @Autowired
    private ScriptedOrdering ordering;

    @Autowired
    private PaymentOrderCreation creation;

    @Autowired
    private InvoiceIssuing issuing;

    @Autowired
    private TaskCancellationService cancellation;

    @Autowired
    private RequestStatusPublication statuses;

    @Autowired
    private TransactionTemplate transaction;

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
        stand.remove(request);
    }

    @Test
    void cancellationCommittedWhileOrderingRejectsTheInvoice_isAppliedWithItsOwnReasonAndMoment() {
        long messagesBefore = stand.statusMessages(request);
        ordering.duringCheckout(() -> {
            cancelRequest();
            assertThat(stand.invoice(contractId).get("deferred_cancel_reason")).isEqualTo("INSURED_REFUSED");
            throw new OrderingRejectedException(OrderingOperation.CHECKOUT, CODE);
        });

        issuing.issueDue();

        Map<String, Object> contract = stand.contract(contractId);
        assertThat(contract.get("status")).isEqualTo("CANCELLED");
        assertThat(contract.get("cancel_reason")).isEqualTo("INSURED_REFUSED");
        assertThat(((Timestamp) contract.get("cancelled_at")).toInstant()).isEqualTo(CANCELLED_AT);
        Map<String, Object> invoice = stand.invoice(contractId);
        assertThat(invoice.get("state")).isEqualTo("REJECTED");
        assertThat(invoice.get("rejection_code")).isEqualTo(CODE);
        assertThat(invoice.get("deferred_cancel_reason")).isNull();
        assertThat(invoice.get("deferred_cancelled_at")).isNull();
        Map<String, Object> order = stand.order(contractId);
        assertThat(order.get("state")).isEqualTo("CANCEL_PENDING");
        assertThat(order.get("payer_email")).isNull();
        assertThat(stand.statusMessages(request)).isEqualTo(messagesBefore + 1);
        assertThat(ordering.checkouts()).isEqualTo(1);
    }

    @Test
    void rejectionWithoutCancellation_leavesTheContractAcceptedWithItsOrderAndPayer() {
        ordering.duringCheckout(() -> {
            throw new OrderingRejectedException(OrderingOperation.CHECKOUT, CODE);
        });

        issuing.issueDue();

        assertThat(stand.contract(contractId).get("status")).isEqualTo("ACCEPTED");
        assertThat(stand.invoice(contractId).get("state")).isEqualTo("REJECTED");
        Map<String, Object> order = stand.order(contractId);
        assertThat(order.get("state")).isEqualTo("CREATED");
        assertThat(order.get("payer_email")).isEqualTo(PaymentRaceStand.EMAIL);
    }

    private void cancelRequest() {
        transaction.executeWithoutResult(status -> {
            cancellation.apply(new ContractTaskCancellation(request, CANCELLED_AT, "INSURED_REFUSED", null),
                    FixedClockConfig.NOW);
            statuses.afterCancellation(request, FixedClockConfig.NOW);
        });
    }
}
