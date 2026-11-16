package ru.projectx.contracts.service.payment.cancellation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.Limit;
import ru.projectx.contracts.entity.payment.OrderRequest;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingRejectedException;
import ru.projectx.contracts.exception.OrderingUnavailableException;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;
import ru.projectx.contracts.service.payment.Ordering;
import ru.projectx.contracts.service.payment.OrderingAttempts;
import ru.projectx.contracts.service.payment.OrderingStepRunner;
import ru.projectx.contracts.service.payment.OrderingSession;
import ru.projectx.contracts.service.payment.PaymentOrderPasses;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.OrderingTestProperties;
import ru.projectx.contracts.support.PaymentOrders;
import ru.projectx.contracts.support.Tasks;

class PaymentOrderCancellingTest {

    private static final Instant NOW = Instant.parse("2026-02-12T12:00:00Z");
    private static final OrderingUnavailableException UNAVAILABLE =
            new OrderingUnavailableException(OrderingOperation.CANCEL_ORDER, "HTTP 503");

    private final PaymentOrderRepository orders = mock();
    private final Ordering ordering = mock();
    private final OrderingSession session = mock();
    private final PaymentOrderCancelRecording recording = mock();
    private final BackgroundLeadership leadership = mock();
    private final PaymentOrderCancelling cancelling = new PaymentOrderCancelling(new PaymentOrderPasses(orders, leadership,
            new OrderingStepRunner(ordering, new OrderingAttempts(OrderingTestProperties.attempts(3))),
            OrderingTestProperties.passes()), recording);

    private final CapturedLog log = CapturedLog.of(OrderingStepRunner.class);

    @org.junit.jupiter.api.AfterEach
    void releaseLog() {
        log.close();
    }

    @BeforeEach
    void leaderWithSession() {
        given(leadership.countAsLeader(any())).willAnswer(pass -> pass.<IntSupplier>getArgument(0).getAsInt());
        given(ordering.session(PaymentOrders.CORRELATION)).willReturn(session);
        given(recording.requestCancellation(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID)).willReturn(true);
    }

    @Test
    void knownOrder_isMarkedThenCancelledAndRecorded() {
        pending(PaymentOrders.cancelPending());

        assertThat(cancelling.cancelDue()).isEqualTo(1);

        InOrder order = inOrder(recording, session);
        order.verify(recording).requestCancellation(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID);
        order.verify(session).cancelOrder(PaymentOrders.ORDER_ID);
        order.verify(recording).cancelled(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID);
        verify(session, never()).orderStatus(any());
        verify(session, never()).findOrder(any(), any());
        verify(orders).findByStateOrderByCreatedAtAscIdAsc(PaymentOrderState.CANCEL_PENDING,
                Limit.of(OrderingTestProperties.BATCH));
    }

    @Test
    void lostCancelAnswer_isFollowedByStateRequestAndNoSecondCancelWhenAlreadyCancelled() {
        pending(PaymentOrders.cancelPending());
        org.mockito.BDDMockito.willThrow(UNAVAILABLE).given(session).cancelOrder(PaymentOrders.ORDER_ID);
        given(session.orderStatus(PaymentOrders.ORDER_ID)).willReturn("CANCELED");

        cancelling.cancelDue();

        verify(session, times(1)).cancelOrder(PaymentOrders.ORDER_ID);
        verify(recording).cancelled(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID);
    }

    @Test
    void cancellationSentEarlierAndOrderStillNew_isCancelledAgain() {
        PaymentOrder order = PaymentOrders.cancelPending();
        order.markRequested(OrderRequest.CANCELLATION, NOW);
        pending(order);
        given(session.orderStatus(PaymentOrders.ORDER_ID)).willReturn("NEW");

        cancelling.cancelDue();

        InOrder sequence = inOrder(session);
        sequence.verify(session).orderStatus(PaymentOrders.ORDER_ID);
        sequence.verify(session).cancelOrder(PaymentOrders.ORDER_ID);
        verify(recording).cancelled(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID);
    }

    @Test
    void temporaryFailures_leaveTheOrderPendingAfterTheConfiguredAttempts() {
        pending(PaymentOrders.cancelPending());
        org.mockito.BDDMockito.willThrow(UNAVAILABLE).given(session).cancelOrder(PaymentOrders.ORDER_ID);
        given(session.orderStatus(PaymentOrders.ORDER_ID)).willReturn("NEW");

        cancelling.cancelDue();

        verify(session, times(3)).cancelOrder(PaymentOrders.ORDER_ID);
        verify(session, times(2)).orderStatus(PaymentOrders.ORDER_ID);
        assertThat(log.at(ch.qos.logback.classic.Level.WARN)).containsExactly("заказ на оплату договора КМ "
                + PaymentOrders.CONTRACT + " не отменён: сервис управления заказами недоступен: cancel_order, HTTP 503,"
                + " проход остановлен до следующего раза");
        verify(recording, never()).cancelled(any(), any());
        verify(recording, never()).rejected(any(), any());
    }

    @Test
    void orderWithoutId_isFoundBySearchAndCancelled() {
        pending(sentWithoutAnswer());
        given(session.findOrder(Tasks.INSURED, PaymentOrders.ORDER)).willReturn(Optional.of(PaymentOrders.ORDER_ID));

        cancelling.cancelDue();

        verify(session).cancelOrder(PaymentOrders.ORDER_ID);
        verify(recording).cancelled(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID);
    }

    @Test
    void orderWithoutIdNotFound_isClosedWithoutCancelling() {
        pending(sentWithoutAnswer());
        given(session.findOrder(Tasks.INSURED, PaymentOrders.ORDER)).willReturn(Optional.empty());

        cancelling.cancelDue();

        verify(session, never()).cancelOrder(any());
        verify(recording).notFound(PaymentOrders.CONTRACT);
    }

    @Test
    void rejectedCancellation_isRecordedWithItsCodeWithoutRetries() {
        pending(PaymentOrders.cancelPending());
        org.mockito.BDDMockito.willThrow(new OrderingRejectedException(OrderingOperation.CANCEL_ORDER, "C"))
                .given(session).cancelOrder(PaymentOrders.ORDER_ID);

        cancelling.cancelDue();

        verify(session, times(1)).cancelOrder(PaymentOrders.ORDER_ID);
        verify(recording).rejected(PaymentOrders.CONTRACT, "C");
    }

    @Test
    void orderNoLongerPending_isNotCancelled() {
        pending(PaymentOrders.cancelPending());
        given(recording.requestCancellation(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID)).willReturn(false);

        cancelling.cancelDue();

        verify(session, never()).cancelOrder(any());
        verify(recording, never()).cancelled(any(), any());
        verify(recording, never()).notFound(any());
    }

    @Test
    void standby_cancelsNothing() {
        org.mockito.BDDMockito.willReturn(0).given(leadership).countAsLeader(any());

        assertThat(cancelling.cancelDue()).isZero();

        verifyNoInteractions(orders, ordering, recording);
    }

    private static PaymentOrder sentWithoutAnswer() {
        PaymentOrder order = PaymentOrders.awaiting();
        order.markRequested(OrderRequest.CREATION, NOW);
        order.withdraw(NOW);
        return order;
    }

    private void pending(PaymentOrder order) {
        given(orders.findByStateOrderByCreatedAtAscIdAsc(PaymentOrderState.CANCEL_PENDING,
                Limit.of(OrderingTestProperties.BATCH))).willReturn(List.of(order));
    }
}
