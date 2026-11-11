package ru.projectx.contracts.service.payment.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.payment.InvoiceLink;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.InvoiceTransition;
import ru.projectx.contracts.entity.payment.InvoiceView;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.service.leadership.LeadershipFence;
import ru.projectx.contracts.service.leadership.LeadershipLostException;
import ru.projectx.contracts.service.payment.CheckoutRequest;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.NewOrder;
import ru.projectx.contracts.service.payment.PaymentOrderLocks;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.PaymentOrders;
import ru.projectx.contracts.support.Tasks;

class InvoiceRecordingTest {

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
    private final IssuanceSnapshot before = mock();
    private final CapturedLog log = CapturedLog.of(InvoiceRecording.class);

    @AfterEach
    void releaseLog() {
        log.close();
    }

    private OrderInvoice locked(OrderInvoice invoice) {
        given(locks.lockInvoice(PaymentOrders.CONTRACT)).willReturn(new LockedInvoice(contract, order, invoice));
        given(statuses.watch(Tasks.REQUEST)).willReturn(before);
        return invoice;
    }

    @Test
    void start_claimsTheCheckoutUnderTheLocksAndTheFence() {
        OrderInvoice invoice = locked(Invoices.ready());
        CheckoutRequest request = new CheckoutRequest(PaymentOrders.ORDER_ID,
                new NewOrder(Tasks.INSURED, PaymentOrders.ORDER, PaymentOrders.DOCUMENT), Invoices.DEADLINE,
                PaymentOrders.PAYER);
        given(claims.claim(new LockedInvoice(contract, order, invoice), NOW)).willReturn(Optional.of(request));

        assertThat(recording.start(PaymentOrders.CONTRACT)).contains(request);

        InOrder sequence = inOrder(locks, fence, claims);
        sequence.verify(locks).lockInvoice(PaymentOrders.CONTRACT);
        sequence.verify(fence).holdUntilCommit();
        sequence.verify(claims).claim(new LockedInvoice(contract, order, invoice), NOW);
    }

    @Test
    void start_whenLeadershipIsLost_claimsNothing() {
        locked(Invoices.ready());
        willThrow(new LeadershipLostException("stand")).given(fence).holdUntilCommit();

        assertThatThrownBy(() -> recording.start(PaymentOrders.CONTRACT)).isInstanceOf(LeadershipLostException.class);

        verifyNoInteractions(claims);
    }

    @Test
    void issued_keepsTheLinkForgetsThePayerAdvancesTheContractAndTellsRequests() {
        OrderInvoice invoice = locked(Invoices.inCheckout());

        recording.issued(PaymentOrders.CONTRACT, Invoices.LINK);

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.ISSUED);
        assertThat(invoice.view().link()).contains(Invoices.LINK);
        assertThat(Invoices.updatedAt(invoice)).isEqualTo(NOW);
        assertThat(order.view().payer()).isEmpty();
        assertThat(contract.status()).isEqualTo(ContractStatus.INVOICE_ISSUED);
        InOrder sequence = inOrder(locks, fence, statuses);
        sequence.verify(locks).lockInvoice(PaymentOrders.CONTRACT);
        sequence.verify(fence).holdUntilCommit();
        sequence.verify(statuses).watch(Tasks.REQUEST);
        sequence.verify(statuses).afterRecordChange(before, NOW);
        assertThat(log.at(Level.INFO)).containsExactly(Invoices.logLine("счёт выставлен, invoiceId inv-0001"));
        assertThat(log.events()).hasSize(1);
    }

    @Test
    void issued_takesTheSnapshotForRequestsBeforeTheContractChanges() {
        locked(Invoices.inCheckout());
        given(statuses.watch(Tasks.REQUEST)).willAnswer(watch -> {
            assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
            return before;
        });

        recording.issued(PaymentOrders.CONTRACT, Invoices.LINK);

        verify(statuses).afterRecordChange(before, NOW);
    }

    @Test
    void issued_forContractThatIsNoLongerAccepted_keepsTheLinkWithoutMovingTheContract() {
        OrderInvoice invoice = locked(Invoices.inCheckout());
        contract.cancel(Invoices.REFUSED, Invoices.STARTED);

        recording.issued(PaymentOrders.CONTRACT, Invoices.LINK);

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.ISSUED);
        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(order.view().payer()).isEmpty();
    }

    @Test
    void issued_afterTheEventSaidPaymentIsAwaited_recordsTheLateLinkAndTellsRequests() {
        OrderInvoice invoice = locked(Invoices.inCheckout());
        recording.paymentAwaited(PaymentOrders.CONTRACT);

        recording.issued(PaymentOrders.CONTRACT, Invoices.LINK);

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.ISSUED);
        assertThat(invoice.view().link()).contains(Invoices.LINK);
        assertThat(contract.status()).isEqualTo(ContractStatus.INVOICE_ISSUED);
        assertThat(order.view().payer()).isEmpty();
        verify(statuses).afterRecordChange(before, NOW);
        assertThat(log.at(Level.INFO)).containsExactly(Invoices.logLine("счёт выставлен, invoiceId inv-0001"));
    }

    @Test
    void issued_forInvoiceInPaymentErrorWithoutLink_recordsTheLateLinkAndKeepsTheState() {
        OrderInvoice invoice = Invoices.inCheckout();
        invoice.pass(InvoiceTransition.REPLY_LOST, Invoices.STARTED);
        invoice.pass(InvoiceTransition.PAYMENT_FAILED, Invoices.STARTED);
        locked(invoice);

        recording.issued(PaymentOrders.CONTRACT, Invoices.LINK);

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.PAYMENT_ERROR);
        assertThat(invoice.view().link()).contains(Invoices.LINK);
        verify(statuses).afterRecordChange(before, NOW);
    }

    @ParameterizedTest
    @EnumSource(names = {"READY", "REJECTED", "PAID", "OVERDUE"})
    void issued_forInvoiceThatTakesNoLink_changesNothingAndSaysWhere(InvoiceState state) {
        OrderInvoice invoice = locked(Invoices.in(state));
        InvoiceView unchanged = invoice.view();

        recording.issued(PaymentOrders.CONTRACT, new InvoiceLink("inv-0002", "https://pay.example.test/pay/inv-0002"));

        assertThat(invoice.view()).isEqualTo(unchanged);
        assertThat(order.view().payer()).contains(PaymentOrders.PAYER);
        assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
        verify(fence).holdUntilCommit();
        verify(statuses, never()).afterRecordChange(before, NOW);
        assertThat(log.at(Level.INFO)).containsExactly(
                Invoices.logLine("ответ на выставление счёта пришёл по счёту в состоянии " + state));
        assertThat(log.events()).hasSize(1);
    }

    @Test
    void issued_repeatedForInvoiceThatHasItsLink_keepsTheFirstLinkAndTellsRequestsNothing() {
        OrderInvoice invoice = locked(Invoices.issued());

        recording.issued(PaymentOrders.CONTRACT, new InvoiceLink("inv-0002", "https://pay.example.test/pay/inv-0002"));

        assertThat(invoice.view().link()).contains(Invoices.LINK);
        assertThat(Invoices.updatedAt(invoice)).isEqualTo(Invoices.STARTED);
        verify(statuses, never()).afterRecordChange(before, NOW);
        assertThat(log.at(Level.INFO)).containsExactly(
                Invoices.logLine("ответ на выставление счёта пришёл по счёту в состоянии ISSUED"));
    }

    @Test
    void issued_whenLeadershipIsLost_recordsNothing() {
        OrderInvoice invoice = locked(Invoices.inCheckout());
        willThrow(new LeadershipLostException("stand")).given(fence).holdUntilCommit();

        assertThatThrownBy(() -> recording.issued(PaymentOrders.CONTRACT, Invoices.LINK))
                .isInstanceOf(LeadershipLostException.class);

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.CHECKOUT);
        assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
        verifyNoInteractions(statuses);
    }
}
