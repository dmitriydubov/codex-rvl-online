package ru.projectx.contracts.service.payment.order;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.Assertions;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.InvoiceView;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.repository.payment.OrderInvoiceRepository;
import ru.projectx.contracts.service.leadership.LeadershipFence;
import ru.projectx.contracts.service.leadership.LeadershipLostException;
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.service.payment.OrderingMetrics;
import ru.projectx.contracts.service.payment.PaymentOrderLocks;
import ru.projectx.contracts.service.payment.invoice.PaymentTermExpiry;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.PaymentOrders;

class PaymentOrderRecordingTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:10:00Z");

    private final PaymentOrderLocks locks = mock();
    private final DocumentFixes fixes = mock();
    private final OrderInvoiceRepository invoices = mock();
    private final PaymentTermExpiry expiry = mock();
    private final OrderingMetrics metrics = mock();
    private final LeadershipFence fence = mock();
    private final PaymentOrderRecording recording = new PaymentOrderRecording(locks, fixes, invoices, expiry, metrics,
            fence, Clock.fixed(NOW, ZoneOffset.UTC));
    private final Contract contract = PaymentOrders.acceptedContract();
    private CapturedLog log;

    @BeforeEach
    void captureLog() {
        log = CapturedLog.of(PaymentOrderRecording.class);
    }

    @AfterEach
    void releaseLog() {
        log.close();
    }

    @Test
    void requestCreation_ofAwaitingOrder_marksItUnderTheFence() {
        PaymentOrder order = locked(PaymentOrders.awaiting());

        assertThat(recording.requestCreation(PaymentOrders.CONTRACT)).isEqualTo(CreationClaim.FIRST);

        assertThat(order.view().creationRequested()).isTrue();
        verify(fence).holdUntilCommit();
    }

    @Test
    void requestCreation_ofAlreadyRequestedOrder_saysItIsARepeat() {
        PaymentOrder order = PaymentOrders.awaiting();
        order.markRequested(NOW);
        locked(order);

        assertThat(recording.requestCreation(PaymentOrders.CONTRACT)).isEqualTo(CreationClaim.REPEATED);

        assertThat(order.view().creationRequested()).isTrue();
    }

    @Test
    void requestCreation_ofWithdrawnOrder_refusesWithoutTouchingIt() {
        PaymentOrder order = PaymentOrders.awaiting();
        order.withdraw(NOW);
        locked(order);

        assertThat(recording.requestCreation(PaymentOrders.CONTRACT)).isEqualTo(CreationClaim.WITHDRAWN);

        assertThat(order.view().creationRequested()).isFalse();
        verifyNoInteractions(fence);
    }

    @Test
    void created_awaitingOrder_recordsTheOrderOpensItsInvoiceAndJournalsTheDocumentFixAfterTheFence() {
        PaymentOrder order = locked(PaymentOrders.awaiting());

        recording.created(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID);

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CREATED);
        assertThat(order.view().orderId()).contains(PaymentOrders.ORDER_ID);
        ArgumentCaptor<OrderInvoice> opened = ArgumentCaptor.forClass(OrderInvoice.class);
        InOrder sequence = inOrder(locks, fence, invoices, fixes);
        sequence.verify(locks).lock(PaymentOrders.CONTRACT);
        sequence.verify(fence).holdUntilCommit();
        sequence.verify(invoices).save(opened.capture());
        sequence.verify(fixes).record(contract, order, NOW);
        assertThat(opened.getValue().view()).isEqualTo(new InvoiceView(PaymentOrders.ORDER, PaymentOrders.CONTRACT,
                InvoiceState.AWAITING_DOCUMENT, 0, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty()));
        assertThat(log.at(Level.INFO)).containsExactly("заказ на оплату договора КМ " + PaymentOrders.CONTRACT
                + " участника 9275471c-d2fe-484f-97a6-d6d7b1267b5e заявки 61a7982c-a0b4-4bbf-9527-60b40b752803: "
                + "заказ создан в Ordering, orderId " + PaymentOrders.ORDER_ID);
    }

    @Test
    void created_whileCancelPending_keepsTheOrderIdWithoutDocumentFix() {
        PaymentOrder order = PaymentOrders.awaiting();
        order.markRequested(NOW);
        order.withdraw(NOW);
        locked(order);

        recording.created(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID);

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
        assertThat(order.view().orderId()).contains(PaymentOrders.ORDER_ID);
        verifyNoInteractions(fixes, invoices);
        assertThat(log.at(Level.INFO)).singleElement().asString().endsWith(": Ordering вернул orderId "
                + PaymentOrders.ORDER_ID + " по заказу в состоянии CANCEL_PENDING");
    }

    @Test
    void lateAnswerForAClosedRecord_returnsItToCancellationWithoutDocumentFix() {
        PaymentOrder order = PaymentOrders.awaiting();
        order.withdraw(NOW);
        locked(order);

        recording.created(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID);

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
        assertThat(order.view().orderId()).contains(PaymentOrders.ORDER_ID);
        verifyNoInteractions(fixes, invoices);
    }

    @Test
    void created_whenLeadershipIsLost_journalsNothing() {
        PaymentOrder order = locked(PaymentOrders.awaiting());
        willThrow(new LeadershipLostException("stand")).given(fence).holdUntilCommit();

        Assertions.assertThatThrownBy(() -> recording.created(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID))
                .isInstanceOf(LeadershipLostException.class);

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.AWAITING_CREATION);
        verifyNoInteractions(fixes, invoices);
    }

    @Test
    void rejected_awaitingOrder_isRejectedCountedAndLoggedAsError() {
        PaymentOrder order = locked(PaymentOrders.awaiting());

        recording.rejected(PaymentOrders.CONTRACT, "CreateOrderValidatorService.valid");

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.REJECTED);
        verify(fence).holdUntilCommit();
        verify(metrics).countOrderRejected();
        assertThat(log.at(Level.ERROR)).singleElement().asString()
                .endsWith(": Ordering отклонил создание заказа, код CreateOrderValidatorService.valid");
    }

    @Test
    void rejected_whileCancelPending_leavesTheRecordForTheCancellationSearchWithoutAlert() {
        PaymentOrder order = PaymentOrders.awaiting();
        order.markRequested(NOW);
        order.withdraw(NOW);
        locked(order);

        recording.rejected(PaymentOrders.CONTRACT, "C");

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
        assertThat(order.view().rejectionCode()).isEmpty();
        verify(metrics, never()).countOrderRejected();
        assertThat(log.at(Level.ERROR)).isEmpty();
        assertThat(log.at(Level.INFO)).singleElement().asString()
                .endsWith(": Ordering отклонил создание заказа к отмене, код C: запись ждёт поиска при отмене");
    }

    @Test
    void rejected_closedOrder_changesNothing() {
        PaymentOrder order = PaymentOrders.awaiting();
        order.withdraw(NOW);
        locked(order);

        recording.rejected(PaymentOrders.CONTRACT, "C");

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CANCELLED);
        verify(metrics, never()).countOrderRejected();
        assertThat(log.events()).isEmpty();
        verifyNoInteractions(fixes, invoices);
    }

    @Test
    void expired_ofAwaitingOrder_closesItByTermBeforeTheOrderUnderTheFence() {
        PaymentOrder order = locked(PaymentOrders.awaiting());

        recording.expired(PaymentOrders.CONTRACT);

        InOrder sequence = inOrder(locks, fence, expiry);
        sequence.verify(locks).lock(PaymentOrders.CONTRACT);
        sequence.verify(fence).holdUntilCommit();
        sequence.verify(expiry).close(new LockedOrder(contract, order),
                "срок оплаты истёк до создания заказа, договор прекращён", NOW);
    }

    @Test
    void expired_ofOrderThatNoLongerAwaitsCreation_changesNothing() {
        PaymentOrder order = locked(PaymentOrders.created());

        recording.expired(PaymentOrders.CONTRACT);

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CREATED);
        verify(fence).holdUntilCommit();
        verifyNoInteractions(expiry);
    }

    @Test
    void expired_whenLeadershipIsLost_closesNothing() {
        locked(PaymentOrders.awaiting());
        willThrow(new LeadershipLostException("stand")).given(fence).holdUntilCommit();

        Assertions.assertThatThrownBy(() -> recording.expired(PaymentOrders.CONTRACT))
                .isInstanceOf(LeadershipLostException.class);

        verifyNoInteractions(expiry);
    }

    private PaymentOrder locked(PaymentOrder order) {
        given(locks.lock(PaymentOrders.CONTRACT)).willReturn(new LockedOrder(contract, order));
        return order;
    }
}
