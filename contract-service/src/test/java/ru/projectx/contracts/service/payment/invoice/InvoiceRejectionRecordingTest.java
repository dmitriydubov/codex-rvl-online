package ru.projectx.contracts.service.payment.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.service.leadership.LeadershipFence;
import ru.projectx.contracts.service.leadership.LeadershipLostException;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.PaymentOrderLocks;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.PaymentOrders;

class InvoiceRejectionRecordingTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:30:00Z");
    private static final String CODE = "CheckoutValidatorService.valid";

    private final PaymentOrderLocks locks = mock();
    private final CheckoutClaims claims = mock();
    private final RequestStatusPublication statuses = mock();
    private final InvoiceAlerts alerts = mock();
    private final LeadershipFence fence = mock();
    private final InvoiceRecording recording = new InvoiceRecording(locks, claims, statuses, alerts, fence,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final Contract contract = PaymentOrders.acceptedContract();
    private final PaymentOrder order = PaymentOrders.created();
    private final CapturedLog log = CapturedLog.of(InvoiceRecording.class);

    @AfterEach
    void releaseLog() {
        log.close();
    }

    private OrderInvoice locked(OrderInvoice invoice) {
        given(locks.lockInvoice(PaymentOrders.CONTRACT)).willReturn(new LockedInvoice(contract, order, invoice));
        return invoice;
    }

    @Test
    void rejected_marksTheInvoiceWithTheCodeKeepsTheContractTheOrderAndThePayerAndAlerts() {
        OrderInvoice invoice = locked(Invoices.inCheckout());

        recording.rejected(PaymentOrders.CONTRACT, CODE);

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.REJECTED);
        assertThat(invoice.view().rejectionCode()).contains(CODE);
        assertThat(Invoices.updatedAt(invoice)).isEqualTo(NOW);
        assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CREATED);
        assertThat(order.view().payer()).contains(PaymentOrders.PAYER);
        InOrder sequence = inOrder(locks, fence, alerts);
        sequence.verify(locks).lockInvoice(PaymentOrders.CONTRACT);
        sequence.verify(fence).holdUntilCommit();
        sequence.verify(alerts).invoiceRejected(contract, CODE);
        verifyNoInteractions(statuses);
        assertThat(log.events()).isEmpty();
    }

    @Test
    void rejected_withDeferredCancellation_alertsStopsTheContractAndSendsTheOrderToCancellation() {
        OrderInvoice invoice = Invoices.inCheckout();
        invoice.deferCancellation(Invoices.REFUSED, Invoices.STARTED);
        locked(invoice);

        recording.rejected(PaymentOrders.CONTRACT, CODE);

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.REJECTED);
        assertThat(invoice.view().rejectionCode()).contains(CODE);
        assertThat(invoice.view().deferredCancellation()).isEmpty();
        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.state().cancellation()).contains(Invoices.REFUSED);
        assertThat(contract.state().updatedAt()).isEqualTo(NOW);
        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
        assertThat(order.view().payer()).isEmpty();
        verify(alerts).invoiceRejected(contract, CODE);
        verifyNoInteractions(statuses);
        assertThat(log.at(Level.INFO)).containsExactly(
                Invoices.logLine("счёт отклонён, отложенное прекращение договора применено"));
        assertThat(log.events()).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "CHECKOUT")
    void rejected_forInvoiceThatIsNotInCheckout_changesNothingAndKeepsTheCancellationDeferred(InvoiceState state) {
        OrderInvoice invoice = Invoices.in(state);
        invoice.deferCancellation(Invoices.REFUSED, Invoices.STARTED);
        locked(invoice);

        recording.rejected(PaymentOrders.CONTRACT, CODE);

        assertThat(invoice.view().state()).isEqualTo(state);
        assertThat(invoice.view().deferredCancellation()).contains(Invoices.REFUSED);
        assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CREATED);
        assertThat(order.view().payer()).contains(PaymentOrders.PAYER);
        verify(fence).holdUntilCommit();
        verifyNoInteractions(alerts, statuses);
        assertThat(log.events()).isEmpty();
    }

    @Test
    void rejected_whenLeadershipIsLost_recordsNothing() {
        OrderInvoice invoice = Invoices.inCheckout();
        invoice.deferCancellation(Invoices.REFUSED, Invoices.STARTED);
        locked(invoice);
        willThrow(new LeadershipLostException("stand")).given(fence).holdUntilCommit();

        assertThatThrownBy(() -> recording.rejected(PaymentOrders.CONTRACT, CODE))
                .isInstanceOf(LeadershipLostException.class);

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.CHECKOUT);
        assertThat(invoice.view().deferredCancellation()).contains(Invoices.REFUSED);
        assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
        verifyNoInteractions(alerts);
    }
}
