package ru.projectx.contracts.service.payment.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.OrderingEvent;
import ru.projectx.contracts.service.payment.OrderingOrder;
import ru.projectx.contracts.service.payment.invoice.InvoiceRecording;
import ru.projectx.contracts.service.payment.invoice.PaymentOutcomes;
import ru.projectx.contracts.support.PaymentOrders;

class OutcomeReconcilersTest {

    private static final Instant NOW = Instant.parse("2026-02-11T10:20:00Z");
    private static final Instant MOMENT = Instant.parse("2026-02-11T10:00:00.250Z");

    private final InvoiceRecording recording = mock();
    private final PaymentOutcomes outcomes = mock();
    private final ReconciliationAlerts alerts = mock();
    private final PendingOrderReconciler pending = new PendingOrderReconciler(recording, alerts);
    private final StateReconciler paid = new OutcomeReconcilers().paidOrderReconciler(outcomes, alerts);
    private final StateReconciler overdue = new OutcomeReconcilers().overdueOrderReconciler(outcomes, alerts);
    private final Contract contract = Invoices.invoicedContract();
    private final PaymentOrder order = PaymentOrders.created();
    private final OrderInvoice issued = Invoices.issued();
    private final LockedInvoice locked = new LockedInvoice(contract, order, issued);

    private static OrderingOrder order(String status, OrderingEvent... history) {
        return new OrderingOrder(status, List.of(history));
    }

    @Test
    void reconcilers_nameTheOrderingStatusesTheyApply() {
        assertThat(pending.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(paid.status()).isEqualTo("PAID");
        assertThat(overdue.status()).isEqualTo("PAYMENT_OVERDUE");
    }

    @Test
    void pendingOrderWhoseInvoiceIsStillInCheckout_meansTheReplyWasLost() {
        LockedInvoice inCheckout = new LockedInvoice(contract, order, Invoices.inCheckout());

        assertThat(pending.reconcile(inCheckout, order("PENDING_PAYMENT"), NOW)).isEqualTo(Followup.NONE);

        verify(recording).replyLost(PaymentOrders.CONTRACT);
        verifyNoInteractions(alerts);
    }

    @ParameterizedTest
    @EnumSource(names = {"ISSUED", "PAYMENT_ERROR"})
    void pendingOrderWhoseInvoiceAwaitsPayment_onlyWaits(InvoiceState state) {
        LockedInvoice awaiting = new LockedInvoice(contract, order, Invoices.in(state));

        assertThat(pending.reconcile(awaiting, order("PENDING_PAYMENT"), NOW)).isEqualTo(Followup.NONE);

        verifyNoInteractions(recording, alerts);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = {"CHECKOUT", "ISSUED", "PAYMENT_ERROR"})
    void pendingOrderWhoseInvoiceDoesNotAwaitPayment_isAnUnexpectedState(InvoiceState state) {
        OrderInvoice invoice = Invoices.in(state);

        assertThat(pending.reconcile(new LockedInvoice(contract, order, invoice), order("PENDING_PAYMENT"), NOW))
                .isEqualTo(Followup.NONE);

        verify(alerts).unexpectedState(contract, invoice.view(), "PENDING_PAYMENT");
        verifyNoInteractions(recording);
    }

    @Test
    void paidOrder_isPaidAtTheLastPaymentMomentOfItsHistory() {
        given(outcomes.paid(locked, MOMENT, NOW)).willReturn(true);
        OrderingOrder state = order("PAID", new OrderingEvent("paid", MOMENT.minusSeconds(60)),
                new OrderingEvent("paid", MOMENT), new OrderingEvent("paymentError", MOMENT.plusSeconds(60)));

        assertThat(paid.reconcile(locked, state, NOW)).isEqualTo(Followup.NONE);

        verify(outcomes).paid(locked, MOMENT, NOW);
        verifyNoInteractions(alerts);
    }

    @Test
    void paidOrderWithoutPaymentInItsHistory_isPaidAtTheMomentOfReconciliation() {
        given(outcomes.paid(locked, NOW, NOW)).willReturn(true);

        paid.reconcile(locked, order("PAID", new OrderingEvent("fixed", MOMENT)), NOW);

        verify(outcomes).paid(locked, NOW, NOW);
        verifyNoInteractions(alerts);
    }

    @Test
    void paidOrderWhosePaymentCannotBeApplied_isAnUnexpectedState() {
        given(outcomes.paid(locked, NOW, NOW)).willReturn(false);

        assertThat(paid.reconcile(locked, order("PAID"), NOW)).isEqualTo(Followup.NONE);

        verify(alerts).unexpectedState(contract, issued.view(), "PAID");
    }

    @Test
    void overdueOrder_isClosedAtTheLastOverdueMomentOfItsHistory() {
        given(outcomes.overdue(locked, MOMENT, NOW)).willReturn(true);
        OrderingOrder state = order("PAYMENT_OVERDUE", new OrderingEvent("paid", MOMENT.plusSeconds(60)),
                new OrderingEvent("paymentOverdue", MOMENT));

        assertThat(overdue.reconcile(locked, state, NOW)).isEqualTo(Followup.NONE);

        verify(outcomes).overdue(locked, MOMENT, NOW);
        verifyNoInteractions(alerts);
    }

    @Test
    void overdueOrderWithoutOverdueInItsHistory_isClosedAtTheMomentOfReconciliation() {
        given(outcomes.overdue(locked, NOW, NOW)).willReturn(true);

        overdue.reconcile(locked, order("PAYMENT_OVERDUE"), NOW);

        verify(outcomes).overdue(locked, NOW, NOW);
        verifyNoInteractions(alerts);
    }

    @Test
    void overdueOrderWhoseOverdueCannotBeApplied_isAnUnexpectedState() {
        given(outcomes.overdue(locked, NOW, NOW)).willReturn(false);

        assertThat(overdue.reconcile(locked, order("PAYMENT_OVERDUE"), NOW)).isEqualTo(Followup.NONE);

        verify(alerts).unexpectedState(contract, issued.view(), "PAYMENT_OVERDUE");
    }
}
