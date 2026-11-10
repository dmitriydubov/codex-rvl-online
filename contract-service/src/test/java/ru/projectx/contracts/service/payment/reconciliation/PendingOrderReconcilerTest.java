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
import ru.projectx.contracts.service.payment.invoice.CheckoutMarks;
import ru.projectx.contracts.service.payment.invoice.InvoiceRecording;
import ru.projectx.contracts.support.OrderingTestProperties;
import ru.projectx.contracts.support.PaymentOrders;

class PendingOrderReconcilerTest {

    private static final OrderingOrder PENDING = new OrderingOrder("PENDING_PAYMENT", List.of());
    private static final Instant THRESHOLD_AFTER_START =
            Invoices.STARTED.plus(OrderingTestProperties.RECONCILIATION_THRESHOLD);

    private final InvoiceRecording recording = mock();
    private final ReconciliationAlerts alerts = mock();
    private final PendingOrderReconciler pending =
            new PendingOrderReconciler(recording, alerts, new CheckoutMarks(OrderingTestProperties.passes()));
    private final Contract contract = Invoices.invoicedContract();
    private final PaymentOrder order = PaymentOrders.created();

    private LockedInvoice locked(OrderInvoice invoice) {
        return new LockedInvoice(contract, order, invoice);
    }

    @Test
    void reconciler_namesTheOrderingStatusItApplies() {
        assertThat(pending.status()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void checkoutStartedExactlyTheThresholdAgo_isALostReply() {
        assertThat(pending.reconcile(locked(Invoices.inCheckout()), PENDING, THRESHOLD_AFTER_START))
                .isEqualTo(Followup.NONE);

        verify(recording).replyLost(PaymentOrders.CONTRACT);
        verifyNoInteractions(alerts);
    }

    @Test
    void checkoutStartedAMillisecondLessThanTheThresholdAgo_isStillInFlight() {
        assertThat(pending.reconcile(locked(Invoices.inCheckout()), PENDING, THRESHOLD_AFTER_START.minusMillis(1)))
                .isEqualTo(Followup.NONE);

        verifyNoInteractions(recording, alerts);
    }

    @ParameterizedTest
    @EnumSource(names = {"ISSUED", "PAYMENT_ERROR"})
    void invoiceThatAlreadyAwaitsPayment_letsTheRecordingDecideWhetherTheReplyWasLostHoweverFreshItIs(
            InvoiceState state) {
        assertThat(pending.reconcile(locked(Invoices.in(state)), PENDING, Invoices.STARTED)).isEqualTo(Followup.NONE);

        verify(recording).replyLost(PaymentOrders.CONTRACT);
        verifyNoInteractions(alerts);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = {"CHECKOUT", "ISSUED", "PAYMENT_ERROR"})
    void invoiceThatDoesNotAwaitPayment_isAnUnexpectedState(InvoiceState state) {
        OrderInvoice invoice = Invoices.in(state);

        assertThat(pending.reconcile(locked(invoice), PENDING, THRESHOLD_AFTER_START.plusSeconds(3600)))
                .isEqualTo(Followup.NONE);

        verify(alerts).unexpectedState(contract, invoice.view(), "PENDING_PAYMENT");
        verifyNoInteractions(recording);
    }
}
