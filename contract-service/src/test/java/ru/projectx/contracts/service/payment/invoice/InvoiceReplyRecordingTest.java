package ru.projectx.contracts.service.payment.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
import ru.projectx.contracts.entity.payment.InvoiceTransition;
import ru.projectx.contracts.entity.payment.InvoiceView;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.service.leadership.LeadershipFence;
import ru.projectx.contracts.service.leadership.LeadershipLostException;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.PaymentOrderLocks;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.PaymentOrders;

class InvoiceReplyRecordingTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:30:00Z");

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
    void paymentAwaited_issuesTheInvoiceWithoutLinkForgetsThePayerAdvancesTheContractAndAlertsNobody() {
        OrderInvoice invoice = locked(Invoices.inCheckout());

        recording.paymentAwaited(PaymentOrders.CONTRACT);

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.ISSUED);
        assertThat(invoice.view().link()).isEmpty();
        assertThat(Invoices.updatedAt(invoice)).isEqualTo(NOW);
        assertThat(order.view().payer()).isEmpty();
        assertThat(contract.status()).isEqualTo(ContractStatus.INVOICE_ISSUED);
        InOrder sequence = inOrder(locks, fence);
        sequence.verify(locks).lockInvoice(PaymentOrders.CONTRACT);
        sequence.verify(fence).holdUntilCommit();
        verifyNoInteractions(alerts, statuses);
        assertThat(log.events()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = "CHECKOUT")
    void paymentAwaited_forInvoiceThatIsNotInCheckout_changesNothing(InvoiceState state) {
        OrderInvoice invoice = locked(Invoices.in(state));
        InvoiceView unchanged = invoice.view();

        recording.paymentAwaited(PaymentOrders.CONTRACT);

        assertThat(invoice.view()).isEqualTo(unchanged);
        assertThat(order.view().payer()).contains(PaymentOrders.PAYER);
        assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
        verify(fence).holdUntilCommit();
        verifyNoInteractions(alerts, statuses);
    }

    @Test
    void paymentAwaited_whenLeadershipIsLost_recordsNothing() {
        OrderInvoice invoice = locked(Invoices.inCheckout());
        willThrow(new LeadershipLostException("stand")).given(fence).holdUntilCommit();

        assertThatThrownBy(() -> recording.paymentAwaited(PaymentOrders.CONTRACT))
                .isInstanceOf(LeadershipLostException.class);

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.CHECKOUT);
        assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
    }

    @Test
    void replyLost_ofInvoiceInCheckout_issuesItWithoutLinkAlertsAndTellsRequestsNothing() {
        OrderInvoice invoice = locked(Invoices.inCheckout());

        recording.replyLost(PaymentOrders.CONTRACT);

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.ISSUED);
        assertThat(invoice.view().link()).isEmpty();
        assertThat(Invoices.updatedAt(invoice)).isEqualTo(NOW);
        assertThat(order.view().payer()).isEmpty();
        assertThat(contract.status()).isEqualTo(ContractStatus.INVOICE_ISSUED);
        InOrder sequence = inOrder(locks, fence, alerts);
        sequence.verify(locks).lockInvoice(PaymentOrders.CONTRACT);
        sequence.verify(fence).holdUntilCommit();
        sequence.verify(alerts).replyLost(contract);
        verifyNoInteractions(statuses);
    }

    @Test
    void replyLost_repeated_alertsOnlyOnce() {
        locked(Invoices.inCheckout());

        recording.replyLost(PaymentOrders.CONTRACT);
        recording.replyLost(PaymentOrders.CONTRACT);

        verify(alerts, times(1)).replyLost(contract);
    }

    @Test
    void replyLost_ofInvoiceThatTheEventIssuedWithoutLink_onlyAlertsAndOnlyOnce() {
        OrderInvoice invoice = locked(Invoices.inCheckout());
        recording.paymentAwaited(PaymentOrders.CONTRACT);
        Instant issuedAt = Invoices.updatedAt(invoice);

        recording.replyLost(PaymentOrders.CONTRACT);
        recording.replyLost(PaymentOrders.CONTRACT);

        verify(alerts, times(1)).replyLost(contract);
        assertThat(invoice.view().state()).isEqualTo(InvoiceState.ISSUED);
        assertThat(invoice.view().link()).isEmpty();
        assertThat(Invoices.updatedAt(invoice)).isEqualTo(issuedAt);
        assertThat(contract.status()).isEqualTo(ContractStatus.INVOICE_ISSUED);
        verifyNoInteractions(statuses);
    }

    @Test
    void replyLost_ofIssuedInvoiceThatHasItsLink_alertsNobody() {
        OrderInvoice invoice = locked(Invoices.issued());

        recording.replyLost(PaymentOrders.CONTRACT);

        assertThat(invoice.view().link()).contains(Invoices.LINK);
        assertThat(order.view().payer()).contains(PaymentOrders.PAYER);
        verify(fence).holdUntilCommit();
        verifyNoInteractions(alerts);
    }

    @Test
    void replyLost_ofInvoiceInPaymentErrorWithoutLink_onlyAlertsAndOnlyOnce() {
        OrderInvoice invoice = locked(Invoices.inCheckout());
        recording.paymentAwaited(PaymentOrders.CONTRACT);
        invoice.pass(InvoiceTransition.PAYMENT_FAILED, Invoices.STARTED);

        recording.replyLost(PaymentOrders.CONTRACT);
        recording.replyLost(PaymentOrders.CONTRACT);

        verify(alerts, times(1)).replyLost(contract);
        assertThat(invoice.view().state()).isEqualTo(InvoiceState.PAYMENT_ERROR);
        assertThat(invoice.view().link()).isEmpty();
        assertThat(Invoices.updatedAt(invoice)).isEqualTo(NOW);
        assertThat(contract.status()).isEqualTo(ContractStatus.INVOICE_ISSUED);
        verifyNoInteractions(statuses);
    }

    @Test
    void replyLost_ofInvoiceInPaymentErrorThatHasItsLink_alertsNobody() {
        OrderInvoice invoice = locked(Invoices.in(InvoiceState.PAYMENT_ERROR));

        recording.replyLost(PaymentOrders.CONTRACT);

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.PAYMENT_ERROR);
        assertThat(invoice.view().link()).contains(Invoices.LINK);
        assertThat(Invoices.updatedAt(invoice)).isEqualTo(Invoices.STARTED);
        verifyNoInteractions(alerts);
    }

    @ParameterizedTest
    @EnumSource(names = {"AWAITING_DOCUMENT", "DOCUMENT_UNCONFIRMED", "READY", "REJECTED", "PAID", "OVERDUE"})
    void replyLost_ofInvoiceThatDoesNotAwaitPayment_changesNothing(InvoiceState state) {
        OrderInvoice invoice = locked(Invoices.in(state));
        InvoiceView unchanged = invoice.view();

        recording.replyLost(PaymentOrders.CONTRACT);

        assertThat(invoice.view()).isEqualTo(unchanged);
        assertThat(order.view().payer()).contains(PaymentOrders.PAYER);
        assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
        verify(fence).holdUntilCommit();
        verifyNoInteractions(alerts);
    }

    @Test
    void replyLost_whenLeadershipIsLost_recordsNothing() {
        OrderInvoice invoice = locked(Invoices.inCheckout());
        willThrow(new LeadershipLostException("stand")).given(fence).holdUntilCommit();

        assertThatThrownBy(() -> recording.replyLost(PaymentOrders.CONTRACT))
                .isInstanceOf(LeadershipLostException.class);

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.CHECKOUT);
        verifyNoInteractions(alerts);
    }
}
