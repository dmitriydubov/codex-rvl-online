package ru.projectx.contracts.service.payment.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.repository.payment.OrderInvoiceRepository;
import ru.projectx.contracts.service.leadership.LeadershipFence;
import ru.projectx.contracts.service.leadership.LeadershipLostException;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.OrderingOrder;
import ru.projectx.contracts.service.payment.PaymentOrderLocks;
import ru.projectx.contracts.service.payment.invoice.PaymentTermExpiry;
import ru.projectx.contracts.service.payment.invoice.PaymentTerms;
import ru.projectx.contracts.support.OrderingTestProperties;
import ru.projectx.contracts.support.PaymentOrders;

class ReconciliationRecordingTest {

    private static final Instant NOW = Instant.parse("2026-02-11T10:20:00Z");
    private static final OrderingOrder PAID = new OrderingOrder("PAID", List.of());

    private final PaymentOrderLocks locks = mock();
    private final OrderInvoiceRepository invoices = mock();
    private final PaymentTermExpiry expiry = mock();
    private final ReconciliationAlerts alerts = mock();
    private final StateReconciler paid = mock();
    private final StateReconciler overdue = mock();
    private final LeadershipFence fence = mock();
    private final Contract contract = Invoices.invoicedContract();
    private final PaymentOrder order = PaymentOrders.created();
    private final OrderInvoice invoice = Invoices.issued();
    private ReconciliationRecording recording;

    @BeforeEach
    void recordingWithTwoReconcilers() {
        given(paid.status()).willReturn("PAID");
        given(overdue.status()).willReturn("PAYMENT_OVERDUE");
        ReconciliationTools tools =
                new ReconciliationTools(new PaymentTerms(OrderingTestProperties.passes()), expiry, alerts);
        recording = new ReconciliationRecording(locks, invoices, tools, List.of(paid, overdue), fence,
                Clock.fixed(NOW, ZoneOffset.UTC));
        given(locks.lockInvoice(PaymentOrders.CONTRACT)).willReturn(new LockedInvoice(contract, order, invoice));
    }

    @Test
    void apply_handsTheLockedInvoiceToTheReconcilerOfTheOrderingStatusUnderTheFence() {
        LockedInvoice locked = new LockedInvoice(contract, order, invoice);
        given(paid.reconcile(locked, PAID, NOW)).willReturn(Followup.CHECKOUT);

        assertThat(recording.apply(PaymentOrders.CONTRACT, PAID)).isEqualTo(Followup.CHECKOUT);

        InOrder sequence = inOrder(locks, fence, paid);
        sequence.verify(locks).lockInvoice(PaymentOrders.CONTRACT);
        sequence.verify(fence).holdUntilCommit();
        sequence.verify(paid).reconcile(locked, PAID, NOW);
        verify(overdue, never()).reconcile(any(), any(), any());
    }

    @Test
    void apply_marksTheInvoiceAsJustCheckedSoThatItIsNotStaleAgain() {
        given(paid.reconcile(any(), any(), any())).willReturn(Followup.NONE);

        recording.apply(PaymentOrders.CONTRACT, PAID);

        assertThat(Invoices.updatedAt(invoice)).isEqualTo(NOW);
    }

    @Test
    void apply_toOrderThatIsNoLongerCreated_onlyMarksTheCheck() {
        order.withdraw(Invoices.STARTED);

        assertThat(recording.apply(PaymentOrders.CONTRACT, PAID)).isEqualTo(Followup.NONE);

        assertThat(Invoices.updatedAt(invoice)).isEqualTo(NOW);
        verify(paid, never()).reconcile(any(), any(), any());
        verifyNoInteractions(alerts);
    }

    @ParameterizedTest
    @EnumSource(names = {"REJECTED", "PAID", "OVERDUE"})
    void apply_toInvoiceThatAwaitsNoOutcome_onlyMarksTheCheckWithoutReconcilerOrAlert(InvoiceState state) {
        OrderInvoice settled = Invoices.in(state);
        given(locks.lockInvoice(PaymentOrders.CONTRACT)).willReturn(new LockedInvoice(contract, order, settled));
        given(paid.reconcile(any(), any(), any())).willReturn(Followup.CHECKOUT);

        assertThat(recording.apply(PaymentOrders.CONTRACT, PAID)).isEqualTo(Followup.NONE);
        assertThat(recording.apply(PaymentOrders.CONTRACT, new OrderingOrder("CANCELED", List.of())))
                .isEqualTo(Followup.NONE);

        assertThat(settled.view().state()).isEqualTo(state);
        assertThat(Invoices.updatedAt(settled)).isEqualTo(NOW);
        verify(paid, never()).reconcile(any(), any(), any());
        verifyNoInteractions(alerts);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = {"REJECTED", "PAID", "OVERDUE"})
    void apply_toInvoiceThatAwaitsItsOutcome_goesToTheReconciler(InvoiceState state) {
        LockedInvoice awaiting = new LockedInvoice(contract, order, Invoices.in(state));
        given(locks.lockInvoice(PaymentOrders.CONTRACT)).willReturn(awaiting);
        given(paid.reconcile(awaiting, PAID, NOW)).willReturn(Followup.CHECKOUT);

        assertThat(recording.apply(PaymentOrders.CONTRACT, PAID)).isEqualTo(Followup.CHECKOUT);
    }

    @Test
    void apply_ofStatusNoReconcilerKnows_isAnUnexpectedState() {
        OrderingOrder canceled = new OrderingOrder("CANCELED", List.of());

        assertThat(recording.apply(PaymentOrders.CONTRACT, canceled)).isEqualTo(Followup.NONE);

        verify(alerts).unexpectedState(contract, invoice.view(), "CANCELED");
        verify(paid, never()).reconcile(any(), any(), any());
    }

    @Test
    void apply_whenLeadershipIsLost_changesNothing() {
        willThrow(new LeadershipLostException("stand")).given(fence).holdUntilCommit();

        assertThatThrownBy(() -> recording.apply(PaymentOrders.CONTRACT, PAID))
                .isInstanceOf(LeadershipLostException.class);

        assertThat(Invoices.updatedAt(invoice)).isEqualTo(Invoices.STARTED);
        verify(paid, never()).reconcile(any(), any(), any());
    }

    @Test
    void stateUnavailable_marksTheCheckAndRaisesTheAlertUnderTheFence() {
        recording.stateUnavailable(PaymentOrders.CONTRACT);

        assertThat(Invoices.updatedAt(invoice)).isEqualTo(NOW);
        InOrder sequence = inOrder(locks, fence, alerts);
        sequence.verify(locks).lockInvoice(PaymentOrders.CONTRACT);
        sequence.verify(fence).holdUntilCommit();
        sequence.verify(alerts).stateUnavailable(contract);
    }

    @Test
    void stateUnavailable_whenLeadershipIsLost_raisesNothing() {
        willThrow(new LeadershipLostException("stand")).given(fence).holdUntilCommit();

        assertThatThrownBy(() -> recording.stateUnavailable(PaymentOrders.CONTRACT))
                .isInstanceOf(LeadershipLostException.class);

        assertThat(Invoices.updatedAt(invoice)).isEqualTo(Invoices.STARTED);
        verifyNoInteractions(alerts);
    }
}
