package ru.projectx.contracts.service.payment.reconciliation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.repository.payment.OrderInvoiceRepository;
import ru.projectx.contracts.service.leadership.LeadershipFence;
import ru.projectx.contracts.service.leadership.LeadershipLostException;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.service.payment.PaymentOrderLocks;
import ru.projectx.contracts.service.payment.invoice.PaymentTermExpiry;
import ru.projectx.contracts.service.payment.invoice.PaymentTerms;
import ru.projectx.contracts.support.OrderingTestProperties;
import ru.projectx.contracts.support.PaymentOrders;

class ExpiredOrderClosingTest {

    private static final Instant BOUNDARY = PaymentOrders.DOCUMENT.documentTtl();
    private static final Instant PAST_BOUNDARY = BOUNDARY.plusNanos(1);
    private static final String BEFORE_INVOICE = "срок оплаты истёк до выставления счёта, договор прекращён";

    private final PaymentOrderLocks locks = mock();
    private final OrderInvoiceRepository invoices = mock();
    private final PaymentTermExpiry expiry = mock();
    private final LeadershipFence fence = mock();
    private final Contract contract = PaymentOrders.acceptedContract();
    private final PaymentOrder order = PaymentOrders.created();

    private ReconciliationRecording recordingAt(Instant now) {
        ReconciliationTools tools = new ReconciliationTools(new PaymentTerms(OrderingTestProperties.passes()), expiry,
                mock(ReconciliationAlerts.class));
        given(locks.lock(PaymentOrders.CONTRACT)).willReturn(new LockedOrder(contract, order));
        return new ReconciliationRecording(locks, invoices, tools, List.of(), fence, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void invoiceIn(InvoiceState state) {
        given(invoices.findByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.of(Invoices.in(state)));
    }

    @ParameterizedTest
    @EnumSource(names = {"AWAITING_DOCUMENT", "DOCUMENT_UNCONFIRMED", "READY", "REJECTED"})
    void orderPastTheBoundaryWhoseInvoiceWasNotStartedOrWasRejected_isClosedByTermUnderTheFence(InvoiceState state) {
        invoiceIn(state);

        recordingAt(PAST_BOUNDARY).closeExpired(PaymentOrders.CONTRACT);

        InOrder sequence = inOrder(locks, fence, expiry);
        sequence.verify(locks).lock(PaymentOrders.CONTRACT);
        sequence.verify(fence).holdUntilCommit();
        sequence.verify(expiry).close(new LockedOrder(contract, order), BEFORE_INVOICE, PAST_BOUNDARY);
    }

    @Test
    void orderPastTheBoundaryWithoutInvoiceRecord_isClosedByTerm() {
        given(invoices.findByContractId(PaymentOrders.CONTRACT)).willReturn(Optional.empty());

        recordingAt(PAST_BOUNDARY).closeExpired(PaymentOrders.CONTRACT);

        verify(expiry).close(new LockedOrder(contract, order), BEFORE_INVOICE, PAST_BOUNDARY);
    }

    @ParameterizedTest
    @EnumSource(names = {"CHECKOUT", "ISSUED", "PAYMENT_ERROR", "PAID", "OVERDUE"})
    void orderWithStartedInvoice_isLeftToItsPaymentOutcome(InvoiceState state) {
        invoiceIn(state);

        recordingAt(PAST_BOUNDARY).closeExpired(PaymentOrders.CONTRACT);

        verify(fence).holdUntilCommit();
        verifyNoInteractions(expiry);
    }

    @Test
    void orderExactlyOnTheBoundary_isNotClosedYet() {
        invoiceIn(InvoiceState.READY);

        recordingAt(BOUNDARY).closeExpired(PaymentOrders.CONTRACT);

        verifyNoInteractions(expiry);
    }

    @Test
    void orderOfContractThatIsNoLongerAccepted_isNotClosed() {
        invoiceIn(InvoiceState.READY);
        contract.cancel(Invoices.REFUSED, Invoices.STARTED);

        recordingAt(PAST_BOUNDARY).closeExpired(PaymentOrders.CONTRACT);

        verifyNoInteractions(expiry);
    }

    @Test
    void orderThatAlreadyLeftTheWork_isNotClosedAgain() {
        invoiceIn(InvoiceState.READY);
        order.withdraw(Invoices.STARTED);

        recordingAt(PAST_BOUNDARY).closeExpired(PaymentOrders.CONTRACT);

        verifyNoInteractions(expiry);
    }

    @Test
    void closing_whenLeadershipIsLost_closesNothing() {
        invoiceIn(InvoiceState.READY);
        ReconciliationRecording recording = recordingAt(PAST_BOUNDARY);
        willThrow(new LeadershipLostException("stand")).given(fence).holdUntilCommit();

        assertThatThrownBy(() -> recording.closeExpired(PaymentOrders.CONTRACT))
                .isInstanceOf(LeadershipLostException.class);

        verifyNoInteractions(expiry);
    }
}
