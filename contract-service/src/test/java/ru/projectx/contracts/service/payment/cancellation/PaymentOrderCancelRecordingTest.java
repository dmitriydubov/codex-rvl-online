package ru.projectx.contracts.service.payment.cancellation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.service.leadership.LeadershipFence;
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.service.payment.OrderingMetrics;
import ru.projectx.contracts.service.payment.PaymentOrderLocks;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.PaymentOrders;

class PaymentOrderCancelRecordingTest {

    private static final Instant NOW = Instant.parse("2026-02-12T12:00:00Z");

    private final PaymentOrderLocks locks = mock();
    private final OrderingMetrics metrics = mock();
    private final LeadershipFence fence = mock();
    private final PaymentOrderCancelRecording recording = new PaymentOrderCancelRecording(locks, metrics, fence,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private CapturedLog log;

    @BeforeEach
    void captureLog() {
        log = CapturedLog.of(PaymentOrderCancelRecording.class);
    }

    @AfterEach
    void releaseLog() {
        log.close();
    }

    @Test
    void requestCancellation_ofPendingOrder_marksItAfterLockAndFence() {
        PaymentOrder order = locked(PaymentOrders.cancelPending());

        assertThat(recording.requestCancellation(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID)).isTrue();

        assertThat(order.view().cancelRequested()).isTrue();
        InOrder sequence = inOrder(locks, fence);
        sequence.verify(locks).lock(PaymentOrders.CONTRACT);
        sequence.verify(fence).holdUntilCommit();
    }

    @Test
    void requestCancellation_ofNotPendingOrder_refuses() {
        PaymentOrder order = locked(PaymentOrders.created());

        assertThat(recording.requestCancellation(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID)).isFalse();

        assertThat(order.view().cancelRequested()).isFalse();
    }

    @Test
    void requestCancellation_ofOrderFoundBySearch_keepsTheFoundOrderIdBeforeTheCancelCall() {
        PaymentOrder order = PaymentOrders.awaiting();
        order.requestCreation(NOW);
        order.withdraw(NOW);
        locked(order);

        assertThat(recording.requestCancellation(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID)).isTrue();

        assertThat(order.view().orderId()).contains(PaymentOrders.ORDER_ID);
        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
        assertThat(order.view().cancelRequested()).isTrue();
    }

    @Test
    void requestCancellation_refused_keepsNoOrderId() {
        PaymentOrder order = PaymentOrders.awaiting();
        locked(order);

        assertThat(recording.requestCancellation(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID)).isFalse();

        assertThat(order.view().orderId()).isEmpty();
    }

    @Test
    void cancelled_closesThePendingOrderKeepingTheFoundOrderId() {
        PaymentOrder order = PaymentOrders.awaiting();
        order.requestCreation(NOW);
        order.withdraw(NOW);
        locked(order);

        recording.cancelled(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID);

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCELLED);
        assertThat(order.view().orderId()).contains(PaymentOrders.ORDER_ID);
        verify(fence).holdUntilCommit();
        assertThat(log.at(Level.INFO)).singleElement().asString()
                .endsWith(": заказ отменён в Ordering, orderId " + PaymentOrders.ORDER_ID);
    }

    @Test
    void cancelled_alreadyClosedOrder_logsNothing() {
        PaymentOrder order = PaymentOrders.cancelPending();
        order.recordCancelled(PaymentOrders.ORDER_ID, NOW);
        locked(order);

        recording.cancelled(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID);

        assertThat(log.events()).isEmpty();
    }

    @Test
    void notFound_closesThePendingOrderWithoutOrderId() {
        PaymentOrder order = PaymentOrders.awaiting();
        order.requestCreation(NOW);
        order.withdraw(NOW);
        locked(order);

        recording.notFound(PaymentOrders.CONTRACT);

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCELLED);
        assertThat(order.view().orderId()).isEmpty();
        verify(fence).holdUntilCommit();
        assertThat(log.at(Level.INFO)).singleElement().asString()
                .endsWith(": заказ в Ordering не найден, запись закрыта без отмены");
    }

    @Test
    void notFound_ofPendingOrderThatGotItsOrderIdMeanwhile_keepsItForTheNextCancellationPass() {
        PaymentOrder order = PaymentOrders.awaiting();
        order.requestCreation(NOW);
        order.withdraw(NOW);
        order.recordOrder(PaymentOrders.ORDER_ID, NOW);
        locked(order);

        recording.notFound(PaymentOrders.CONTRACT);

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
        assertThat(order.view().orderId()).contains(PaymentOrders.ORDER_ID);
        assertThat(log.events()).singleElement().asString()
                .doesNotContain("не найден")
                .endsWith(": заказ получил orderId " + PaymentOrders.ORDER_ID
                        + ", пока шёл поиск: отменится следующим проходом");
    }

    @Test
    void notFound_ofOrderCancelledMeanwhile_logsNothing() {
        PaymentOrder order = PaymentOrders.cancelPending();
        order.recordCancelled(PaymentOrders.ORDER_ID, NOW);
        locked(order);

        recording.notFound(PaymentOrders.CONTRACT);

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCELLED);
        assertThat(log.events()).isEmpty();
    }

    @Test
    void notFound_ofClosedOrder_logsNothing() {
        PaymentOrder order = PaymentOrders.awaiting();
        order.withdraw(NOW);
        locked(order);

        recording.notFound(PaymentOrders.CONTRACT);

        assertThat(log.events()).isEmpty();
    }

    @Test
    void rejected_marksTheOrderCountsAndAlerts() {
        PaymentOrder order = locked(PaymentOrders.cancelPending());

        recording.rejected(PaymentOrders.CONTRACT, "CancelOrderValidatorService.valid");

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCEL_REJECTED);
        verify(metrics).countCancellationRejected();
        verify(fence).holdUntilCommit();
        assertThat(log.at(Level.ERROR)).singleElement().asString()
                .endsWith(": Ordering отклонил отмену заказа, код CancelOrderValidatorService.valid");
    }

    @Test
    void rejected_notPendingOrder_changesNothing() {
        PaymentOrder order = locked(PaymentOrders.created());

        recording.rejected(PaymentOrders.CONTRACT, "C");

        assertThat(order.state()).isEqualTo(PaymentOrderState.CREATED);
        verify(metrics, never()).countCancellationRejected();
        assertThat(log.events()).isEmpty();
    }

    private PaymentOrder locked(PaymentOrder order) {
        given(locks.lock(PaymentOrders.CONTRACT)).willReturn(new LockedOrder(PaymentOrders.acceptedContract(), order));
        return order;
    }
}
