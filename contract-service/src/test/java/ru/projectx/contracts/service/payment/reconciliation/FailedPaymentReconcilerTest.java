package ru.projectx.contracts.service.payment.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.InvoiceTransition;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.OrderingOrder;
import ru.projectx.contracts.service.payment.PaymentMetrics;
import ru.projectx.contracts.service.payment.invoice.InvoiceRecording;
import ru.projectx.contracts.service.payment.invoice.PaymentOutcomes;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.support.OrderingTestProperties;
import ru.projectx.contracts.support.PaymentOrders;
import ru.projectx.contracts.support.Tasks;

class FailedPaymentReconcilerTest {

    private static final OrderingOrder FAILED = new OrderingOrder("PAYMENT_ERROR", List.of());
    private static final Instant BEFORE_DEADLINE = Invoices.DEADLINE.minusSeconds(3600);
    private static final Instant END_OF_GRACE = Invoices.DEADLINE.plus(OrderingTestProperties.PAYMENT_GRACE);

    private final InvoiceRecording recording = mock();
    private final RequestStatusPublication statuses = mock();
    private final PaymentMetrics metrics = mock();
    private final ReconciliationAlerts alerts = mock();
    private final FailedPaymentReconciler reconciler = new FailedPaymentReconciler(recording,
            new PaymentOutcomes(statuses, metrics), alerts, OrderingTestProperties.passes());
    private final Contract contract = Invoices.invoicedContract();
    private final PaymentOrder order = PaymentOrders.created();

    @BeforeEach
    void requestSnapshot() {
        given(statuses.watch(Tasks.REQUEST)).willReturn(mock());
    }

    private LockedInvoice locked(OrderInvoice invoice) {
        return new LockedInvoice(contract, order, invoice);
    }

    @Test
    void reconciler_namesTheOrderingStatusItApplies() {
        assertThat(reconciler.status()).isEqualTo("PAYMENT_ERROR");
    }

    @Test
    void failedPaymentWhoseEventWasLost_isMarkedAndCountedAndTheContractKeepsWaiting() {
        OrderInvoice invoice = Invoices.issued();

        assertThat(reconciler.reconcile(locked(invoice), FAILED, BEFORE_DEADLINE)).isEqualTo(Followup.NONE);

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.PAYMENT_ERROR);
        verify(metrics).countPaymentError();
        assertThat(contract.status()).isEqualTo(ContractStatus.INVOICE_ISSUED);
        verifyNoInteractions(alerts, recording);
    }

    @Test
    void failedPaymentAlreadyMarked_isNotCountedAgain() {
        OrderInvoice invoice = Invoices.in(InvoiceState.PAYMENT_ERROR);

        assertThat(reconciler.reconcile(locked(invoice), FAILED, BEFORE_DEADLINE)).isEqualTo(Followup.NONE);

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.PAYMENT_ERROR);
        verify(metrics, never()).countPaymentError();
        verifyNoInteractions(alerts);
    }

    @Test
    void failedPaymentOfInvoiceStillInCheckout_firstRecordsTheLostReply() {
        OrderInvoice invoice = Invoices.inCheckout();
        willAnswer(lost -> invoice.pass(InvoiceTransition.REPLY_LOST, BEFORE_DEADLINE))
                .given(recording).replyLost(PaymentOrders.CONTRACT);

        assertThat(reconciler.reconcile(locked(invoice), FAILED, BEFORE_DEADLINE)).isEqualTo(Followup.NONE);

        verify(recording).replyLost(PaymentOrders.CONTRACT);
        assertThat(invoice.view().state()).isEqualTo(InvoiceState.PAYMENT_ERROR);
        verify(metrics).countPaymentError();
        verifyNoInteractions(alerts);
    }

    @ParameterizedTest
    @EnumSource(names = {"AWAITING_DOCUMENT", "DOCUMENT_UNCONFIRMED", "READY", "REJECTED", "PAID", "OVERDUE"})
    void failedPaymentOfInvoiceThatCannotFail_isAnUnexpectedState(InvoiceState state) {
        OrderInvoice invoice = Invoices.in(state);

        assertThat(reconciler.reconcile(locked(invoice), FAILED, END_OF_GRACE.plusSeconds(1))).isEqualTo(Followup.NONE);

        verify(alerts).unexpectedState(contract, invoice.view(), "PAYMENT_ERROR");
        verify(alerts, never()).paymentErrorOverdue(any());
        assertThat(invoice.view().state()).isEqualTo(state);
        assertThat(contract.status()).isEqualTo(ContractStatus.INVOICE_ISSUED);
        verifyNoInteractions(recording);
    }

    @Test
    void failedPaymentExactlyAtTheEndOfTheGraceAfterTheDeadline_stillWaits() {
        OrderInvoice invoice = Invoices.in(InvoiceState.PAYMENT_ERROR);

        reconciler.reconcile(locked(invoice), FAILED, END_OF_GRACE);

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.PAYMENT_ERROR);
        assertThat(contract.status()).isEqualTo(ContractStatus.INVOICE_ISSUED);
        verifyNoInteractions(alerts);
    }

    @Test
    void failedPaymentWithoutOutcomeAfterTheGrace_isClosedAsOverdueAtTheMomentOfReconciliationWithAnAlert() {
        OrderInvoice invoice = Invoices.in(InvoiceState.PAYMENT_ERROR);
        Instant afterGrace = END_OF_GRACE.plusNanos(1);

        assertThat(reconciler.reconcile(locked(invoice), FAILED, afterGrace)).isEqualTo(Followup.NONE);

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.OVERDUE);
        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.state().cancellation()).contains(new Cancellation(CancelReason.EXPIRED, null, afterGrace));
        verify(alerts).paymentErrorOverdue(contract);
        verify(alerts, never()).unexpectedState(any(), any(), any());
    }

    @Test
    void failedPaymentLearnedOnlyAfterTheGrace_isMarkedCountedAndClosedInOneReconciliation() {
        OrderInvoice invoice = Invoices.issued();

        reconciler.reconcile(locked(invoice), FAILED, END_OF_GRACE.plusSeconds(1));

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.OVERDUE);
        verify(metrics).countPaymentError();
        verify(alerts).paymentErrorOverdue(contract);
    }
}
