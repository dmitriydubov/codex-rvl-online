package ru.projectx.contracts.service.payment.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
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
import ru.projectx.contracts.service.payment.OrderingOrder;
import ru.projectx.contracts.service.payment.order.DocumentFixes;
import ru.projectx.contracts.support.OrderingTestProperties;
import ru.projectx.contracts.support.PaymentOrders;

class DocumentReconcilersTest {

    private static final Instant NOW = Instant.parse("2026-02-10T08:00:00Z");
    private static final OrderingOrder NEW = new OrderingOrder("NEW", List.of());
    private static final OrderingOrder READY = new OrderingOrder("READY_TO_PAYMENT", List.of());

    private final DocumentFixes fixes = mock();
    private final ReconciliationAlerts alerts = mock();
    private final NewOrderReconciler newOrder = new NewOrderReconciler(fixes, alerts, OrderingTestProperties.passes());
    private final ReadyOrderReconciler readyOrder = new ReadyOrderReconciler(alerts);
    private final Contract contract = PaymentOrders.acceptedContract();
    private final PaymentOrder order = PaymentOrders.created();

    private LockedInvoice locked(OrderInvoice invoice) {
        return new LockedInvoice(contract, order, invoice);
    }

    private static OrderInvoice remindedTimes(int repeats) {
        OrderInvoice invoice = Invoices.awaitingDocument();
        for (int repeat = 0; repeat < repeats; repeat++) {
            invoice.repeatFix(Invoices.STARTED);
        }
        return invoice;
    }

    @Test
    void reconcilers_nameTheOrderingStatusesTheyApply() {
        assertThat(newOrder.status()).isEqualTo("NEW");
        assertThat(readyOrder.status()).isEqualTo("READY_TO_PAYMENT");
    }

    @Test
    void newOrderAwaitingTheDocument_getsTheConfirmationRepeatedAndCounted() {
        OrderInvoice invoice = remindedTimes(OrderingTestProperties.FIX_REPEATS - 1);

        assertThat(newOrder.reconcile(locked(invoice), NEW, NOW)).isEqualTo(Followup.NONE);

        verify(fixes).record(contract, order, NOW);
        assertThat(invoice.view().fixRepeats()).isEqualTo(OrderingTestProperties.FIX_REPEATS);
        assertThat(invoice.view().state()).isEqualTo(InvoiceState.AWAITING_DOCUMENT);
        assertThat(Invoices.updatedAt(invoice)).isEqualTo(NOW);
        verifyNoInteractions(alerts);
    }

    @Test
    void newOrderAfterAllRepeats_isMarkedUnconfirmedWithOneAlertAndWithoutAnotherConfirmation() {
        OrderInvoice invoice = remindedTimes(OrderingTestProperties.FIX_REPEATS);

        assertThat(newOrder.reconcile(locked(invoice), NEW, NOW)).isEqualTo(Followup.NONE);

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.DOCUMENT_UNCONFIRMED);
        assertThat(invoice.view().fixRepeats()).isEqualTo(OrderingTestProperties.FIX_REPEATS);
        verify(alerts).documentNotFixed(contract);
        verifyNoInteractions(fixes);
    }

    @Test
    void newOrderAlreadyMarkedUnconfirmed_onlyWaits() {
        OrderInvoice invoice = Invoices.in(InvoiceState.DOCUMENT_UNCONFIRMED);

        assertThat(newOrder.reconcile(locked(invoice), NEW, NOW)).isEqualTo(Followup.NONE);

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.DOCUMENT_UNCONFIRMED);
        verifyNoInteractions(fixes, alerts);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = {"AWAITING_DOCUMENT", "DOCUMENT_UNCONFIRMED"})
    void newOrderWhoseDocumentContractsConsiderConfirmed_isAnUnexpectedState(InvoiceState state) {
        OrderInvoice invoice = Invoices.in(state);

        assertThat(newOrder.reconcile(locked(invoice), NEW, NOW)).isEqualTo(Followup.NONE);

        verify(alerts).unexpectedState(contract, invoice.view(), "NEW");
        assertThat(invoice.view().state()).isEqualTo(state);
        verifyNoInteractions(fixes);
    }

    @ParameterizedTest
    @EnumSource(names = {"AWAITING_DOCUMENT", "DOCUMENT_UNCONFIRMED"})
    void readyOrderWhoseEventWasLost_becomesReadyAndAsksForCheckout(InvoiceState state) {
        OrderInvoice invoice = Invoices.in(state);

        assertThat(readyOrder.reconcile(locked(invoice), READY, NOW)).isEqualTo(Followup.CHECKOUT);

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.READY);
        assertThat(Invoices.updatedAt(invoice)).isEqualTo(NOW);
        verifyNoInteractions(alerts);
    }

    @ParameterizedTest
    @EnumSource(names = {"READY", "CHECKOUT"})
    void readyOrderWhoseCheckoutDidNotReachOrdering_asksForCheckoutAgain(InvoiceState state) {
        OrderInvoice invoice = Invoices.in(state);

        assertThat(readyOrder.reconcile(locked(invoice), READY, NOW)).isEqualTo(Followup.CHECKOUT);

        assertThat(invoice.view().state()).isEqualTo(state);
        verifyNoInteractions(alerts);
    }

    @ParameterizedTest
    @EnumSource(names = {"ISSUED", "REJECTED", "PAYMENT_ERROR", "PAID", "OVERDUE"})
    void readyOrderWhoseInvoiceContractsConsiderIssuedOrClosed_isAnUnexpectedState(InvoiceState state) {
        OrderInvoice invoice = Invoices.in(state);

        assertThat(readyOrder.reconcile(locked(invoice), READY, NOW)).isEqualTo(Followup.NONE);

        verify(alerts).unexpectedState(contract, invoice.view(), "READY_TO_PAYMENT");
        assertThat(invoice.view().state()).isEqualTo(state);
    }
}
