package ru.projectx.contracts.service.payment.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.InvoiceTerm;
import ru.projectx.contracts.entity.payment.InvoiceView;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.service.payment.CheckoutRequest;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.LockedInvoice;
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.service.payment.NewOrder;
import ru.projectx.contracts.support.OrderingTestProperties;
import ru.projectx.contracts.support.PaymentOrders;
import ru.projectx.contracts.support.Tasks;

class CheckoutClaimsTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:30:00Z");
    private static final Instant BY_TERM = Instant.parse("2026-02-13T07:30:00Z");
    private static final Instant BOUNDARY = PaymentOrders.DOCUMENT.documentTtl();
    private static final Instant ABANDONED = Invoices.STARTED.plus(OrderingTestProperties.RECONCILIATION_THRESHOLD);

    private final PaymentTermExpiry expiry = mock();
    private final CheckoutClaims claims = new CheckoutClaims(new PaymentTerms(OrderingTestProperties.passes()), expiry,
            OrderingTestProperties.passes());
    private final Contract contract = PaymentOrders.acceptedContract();
    private final PaymentOrder order = PaymentOrders.created();

    private Optional<CheckoutRequest> claim(OrderInvoice invoice, Instant now) {
        return claims.claim(new LockedInvoice(contract, order, invoice), now);
    }

    @Test
    void readyInvoice_isStartedWithThePaymentDeadlineAndGivesTheCheckoutRequest() {
        OrderInvoice invoice = Invoices.ready();

        Optional<CheckoutRequest> request = claim(invoice, NOW);

        assertThat(request).contains(new CheckoutRequest(PaymentOrders.ORDER_ID,
                new NewOrder(Tasks.INSURED, PaymentOrders.ORDER, PaymentOrders.DOCUMENT), BY_TERM, PaymentOrders.PAYER));
        assertThat(invoice.view().state()).isEqualTo(InvoiceState.CHECKOUT);
        assertThat(invoice.view().term()).contains(new InvoiceTerm(NOW, BY_TERM));
        assertThat(order.view().payer()).contains(PaymentOrders.PAYER);
        assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
        verifyNoInteractions(expiry);
    }

    @Test
    void checkoutStartedExactlyTheReconciliationThresholdAgo_isAbandonedAndClaimedAgainFromTheNewMoment() {
        OrderInvoice invoice = Invoices.inCheckout();

        Optional<CheckoutRequest> request = claim(invoice, ABANDONED);

        Instant deadline = ABANDONED.plus(OrderingTestProperties.PAYMENT_TERM);
        assertThat(request).map(CheckoutRequest::paymentDeadline).contains(deadline);
        assertThat(invoice.view().term()).contains(new InvoiceTerm(ABANDONED, deadline));
    }

    @Test
    void checkoutStartedAMillisecondLessThanTheThresholdAgo_isStillInFlightAndNotClaimed() {
        OrderInvoice invoice = Invoices.inCheckout();

        assertThat(claim(invoice, ABANDONED.minusMillis(1))).isEmpty();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.CHECKOUT);
        assertThat(invoice.view().term()).contains(new InvoiceTerm(Invoices.STARTED, Invoices.DEADLINE));
        assertThat(order.view().payer()).contains(PaymentOrders.PAYER);
        verifyNoInteractions(expiry);
    }

    @Test
    void checkoutInFlightWithDeferredCancellation_keepsTheCancellationDeferred() {
        OrderInvoice invoice = Invoices.inCheckout();
        invoice.deferCancellation(Invoices.REFUSED, Invoices.STARTED);

        assertThat(claim(invoice, NOW)).isEmpty();

        assertThat(invoice.view().deferredCancellation()).contains(Invoices.REFUSED);
        assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CREATED);
    }

    @Test
    void deadline_isTheBoundaryBeforeCheckInWhenItComesBeforeThePaymentTermEnds() {
        OrderInvoice invoice = Invoices.ready();
        Instant dayBefore = BOUNDARY.minusSeconds(86_400);

        Optional<CheckoutRequest> request = claim(invoice, dayBefore);

        assertThat(request).map(CheckoutRequest::paymentDeadline).contains(BOUNDARY);
        assertThat(invoice.view().term()).contains(new InvoiceTerm(dayBefore, BOUNDARY));
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.EXCLUDE, names = {"READY", "CHECKOUT"})
    void invoiceThatDoesNotAwaitCheckout_isNotClaimedHoweverLongAgoItsCheckoutStarted(InvoiceState state) {
        OrderInvoice invoice = Invoices.in(state);
        InvoiceView before = invoice.view();

        assertThat(claim(invoice, ABANDONED.plusSeconds(3600))).isEmpty();

        assertThat(invoice.view()).isEqualTo(before);
        verifyNoInteractions(expiry);
    }

    @Test
    void orderThatIsNoLongerCreated_isNotClaimed() {
        OrderInvoice invoice = Invoices.ready();
        order.withdraw(NOW);

        assertThat(claim(invoice, NOW)).isEmpty();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.READY);
        assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
        verifyNoInteractions(expiry);
    }

    @Test
    void contractThatIsNoLongerAccepted_isNotClaimed() {
        OrderInvoice invoice = Invoices.ready();
        contract.cancel(Invoices.REFUSED, NOW);

        assertThat(claim(invoice, NOW)).isEmpty();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.READY);
        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CREATED);
        verifyNoInteractions(expiry);
    }

    @Test
    void deferredCancellation_isAppliedInsteadOfTheRepeatedCheckoutOfAnAbandonedOne() {
        OrderInvoice invoice = Invoices.inCheckout();
        invoice.deferCancellation(Invoices.REFUSED, Invoices.STARTED);

        assertThat(claim(invoice, ABANDONED)).isEmpty();

        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.state().cancellation()).contains(Invoices.REFUSED);
        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
        assertThat(order.view().payer()).isEmpty();
        assertThat(invoice.view().deferredCancellation()).isEmpty();
        assertThat(invoice.view().term()).contains(new InvoiceTerm(Invoices.STARTED, Invoices.DEADLINE));
        verifyNoInteractions(expiry);
    }

    @Test
    void orderWithoutPayerAddress_isNotClaimedAndNotStarted() {
        OrderInvoice invoice = Invoices.ready();
        order.forgetPayer(NOW);

        assertThat(claim(invoice, NOW)).isEmpty();

        assertThat(invoice.view().state()).isEqualTo(InvoiceState.READY);
        assertThat(contract.status()).isEqualTo(ContractStatus.ACCEPTED);
        verifyNoInteractions(expiry);
    }

    @Test
    void termShorterThanTheMinimum_closesTheOrderByTermInsteadOfTheInvoice() {
        OrderInvoice invoice = Invoices.ready();
        Instant tooLate = BOUNDARY.minus(OrderingTestProperties.MIN_PAYMENT_TERM).plusNanos(1);

        assertThat(claim(invoice, tooLate)).isEmpty();

        verify(expiry).close(new LockedOrder(contract, order),
                "срок оплаты истёк до выставления счёта, договор прекращён", tooLate);
        assertThat(invoice.view().state()).isEqualTo(InvoiceState.READY);
        assertThat(invoice.view().term()).isEmpty();
    }

    @Test
    void termOfExactlyTheMinimum_isStillInvoiced() {
        OrderInvoice invoice = Invoices.ready();
        Instant lastMoment = BOUNDARY.minus(OrderingTestProperties.MIN_PAYMENT_TERM);

        assertThat(claim(invoice, lastMoment)).map(CheckoutRequest::paymentDeadline).contains(BOUNDARY);

        verifyNoInteractions(expiry);
    }
}
