package ru.projectx.contracts.service.payment.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingOrderFailureException;
import ru.projectx.contracts.exception.OrderingUnavailableException;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;
import ru.projectx.contracts.service.payment.Ordering;
import ru.projectx.contracts.service.payment.OrderingAttempts;
import ru.projectx.contracts.service.payment.OrderingOrder;
import ru.projectx.contracts.service.payment.OrderingSession;
import ru.projectx.contracts.service.payment.OrderingStepRunner;
import ru.projectx.contracts.service.payment.PaymentOrderPasses;
import ru.projectx.contracts.service.payment.invoice.InvoiceIssuing;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.OrderingTestProperties;
import ru.projectx.contracts.support.PaymentOrders;

class PaymentReconciliationTest {

    private static final Instant NOW = Instant.parse("2026-02-11T10:20:00Z");
    private static final Limit BATCH = Limit.of(OrderingTestProperties.BATCH);
    private static final UUID FIRST = UUID.fromString("0196f0a2-0000-7000-8000-0000000000c1");
    private static final UUID SECOND = UUID.fromString("0196f0a2-0000-7000-8000-0000000000c2");
    private static final UUID THIRD = UUID.fromString("0196f0a2-0000-7000-8000-0000000000c3");
    private static final OrderingOrder PENDING = new OrderingOrder("PENDING_PAYMENT", List.of());
    private static final OrderingUnavailableException TIMEOUT =
            new OrderingUnavailableException(OrderingOperation.ORDER_STATE, "нет ответа");

    private final PaymentOrderRepository orders = mock();
    private final BackgroundLeadership leadership = mock();
    private final Ordering ordering = mock();
    private final OrderingSession session = mock();
    private final InvoiceIssuing issuing = mock();
    private final ReconciliationRecording recording = mock();
    private final OrderingStepRunner steps = mock();
    private final PaymentReconciliation reconciliation = new PaymentReconciliation(
            new PaymentOrderPasses(orders, leadership, steps, OrderingTestProperties.passes()), orders,
            new OrderingCalls(ordering, new OrderingAttempts(OrderingTestProperties.attempts(3)), issuing), recording,
            OrderingTestProperties.passes(), Clock.fixed(NOW, ZoneOffset.UTC));
    private final CapturedLog log = CapturedLog.of(PaymentReconciliation.class);

    @BeforeEach
    void leaderWithOrderingThatAnswers() {
        given(leadership.countAsLeader(any())).willAnswer(pass -> pass.<IntSupplier>getArgument(0).getAsInt());
        given(ordering.session(PaymentOrders.CORRELATION)).willReturn(session);
        given(session.order(PaymentOrders.ORDER_ID)).willReturn(PENDING);
        given(recording.apply(any(), any())).willReturn(Followup.NONE);
        pastBoundary();
        toReconcile();
    }

    @AfterEach
    void releaseLog() {
        log.close();
    }

    private static PaymentOrder orderOf(UUID contractId) {
        PaymentOrder order = PaymentOrders.created();
        ReflectionTestUtils.setField(order, "contractId", contractId);
        return order;
    }

    private void pastBoundary(PaymentOrder... due) {
        given(orders.findPastBoundary(EnumSet.of(PaymentOrderState.CREATED, PaymentOrderState.REJECTED),
                EnumSet.of(InvoiceState.CHECKOUT, InvoiceState.ISSUED, InvoiceState.PAYMENT_ERROR, InvoiceState.PAID,
                        InvoiceState.OVERDUE), NOW, BATCH)).willReturn(List.of(due));
    }

    private void toReconcile(PaymentOrder... due) {
        given(orders.findWithInvoiceToReconcile(NOW.minus(OrderingTestProperties.RECONCILIATION_THRESHOLD),
                NOW.minus(OrderingTestProperties.PAYMENT_GRACE), BATCH)).willReturn(List.of(due));
    }

    @Test
    void reconciliation_closesOrdersPastTheBoundaryThenReconcilesTheRestAndCountsThemAll() {
        pastBoundary(orderOf(FIRST));
        toReconcile(orderOf(SECOND), orderOf(THIRD), orderOf(THIRD));

        assertThat(reconciliation.reconcileDue()).isEqualTo(4);

        verify(recording).closeExpired(FIRST);
        verify(recording).apply(SECOND, PENDING);
        verify(recording, times(2)).apply(THIRD, PENDING);
        verify(recording, never()).closeExpired(SECOND);
    }

    @Test
    void orderPastTheBoundary_isClosedWithoutAskingOrdering() {
        pastBoundary(orderOf(FIRST), orderOf(SECOND));

        assertThat(reconciliation.reconcileDue()).isEqualTo(2);

        verify(recording).closeExpired(FIRST);
        verify(recording).closeExpired(SECOND);
        verify(recording, never()).apply(any(), any());
        verifyNoInteractions(ordering, issuing);
    }

    @Test
    void orderToReconcile_getsTheStateOfOrderingAppliedWithoutCheckoutUnlessAskedFor() {
        toReconcile(orderOf(FIRST));

        reconciliation.reconcileDue();

        verify(session).order(PaymentOrders.ORDER_ID);
        verify(recording).apply(FIRST, PENDING);
        verify(recording, never()).closeExpired(any());
        verifyNoInteractions(issuing);
    }

    @Test
    void orderWhoseStateAsksForCheckout_isInvoicedRightAway() {
        PaymentOrder ready = orderOf(FIRST);
        toReconcile(ready, orderOf(SECOND));
        given(recording.apply(FIRST, PENDING)).willReturn(Followup.CHECKOUT);
        given(issuing.issue(ready.view())).willReturn(true);

        assertThat(reconciliation.reconcileDue()).isEqualTo(2);

        verify(issuing).issue(ready.view());
        verify(recording).apply(SECOND, PENDING);
    }

    @Test
    void checkoutThatStopsItsPass_stopsTheRestOfReconciliation() {
        PaymentOrder ready = orderOf(FIRST);
        toReconcile(ready, orderOf(SECOND));
        given(recording.apply(FIRST, PENDING)).willReturn(Followup.CHECKOUT);
        given(issuing.issue(ready.view())).willReturn(false);

        assertThat(reconciliation.reconcileDue()).isEqualTo(1);

        verify(recording, never()).apply(SECOND, PENDING);
    }

    @Test
    void orderingUnavailable_isRetriedOnceAlertedOnceAndStopsTheRestOfThePass() {
        toReconcile(orderOf(FIRST), orderOf(SECOND));
        given(session.order(PaymentOrders.ORDER_ID)).willThrow(TIMEOUT);

        assertThat(reconciliation.reconcileDue()).isEqualTo(1);

        verify(session, times(3)).order(PaymentOrders.ORDER_ID);
        verify(recording).stateUnavailable(FIRST);
        verify(recording, never()).stateUnavailable(SECOND);
        verify(recording, never()).apply(any(), any());
        assertThat(log.at(Level.WARN)).containsExactly("состояние заказа на оплату договора КМ " + FIRST
                + " не получено: сервис управления заказами недоступен: order_state, нет ответа");
    }

    @Test
    void tokenServerUnavailable_stopsTheRestOfThePassTheSameWay() {
        toReconcile(orderOf(FIRST), orderOf(SECOND));
        given(ordering.session(PaymentOrders.CORRELATION))
                .willThrow(new OrderingUnavailableException(OrderingOperation.TOKEN, "HTTP 503"));

        assertThat(reconciliation.reconcileDue()).isEqualTo(1);

        verify(recording).stateUnavailable(FIRST);
        verifyNoInteractions(session);
    }

    @Test
    void failureOfOneOrder_isAlertedAndThePassGoesOnToTheNextOrder() {
        toReconcile(orderOf(FIRST), orderOf(SECOND));
        given(session.order(PaymentOrders.ORDER_ID))
                .willThrow(new OrderingOrderFailureException(OrderingOperation.ORDER_STATE, "HTTP 404"))
                .willThrow(new OrderingOrderFailureException(OrderingOperation.ORDER_STATE, "HTTP 404"))
                .willThrow(new OrderingOrderFailureException(OrderingOperation.ORDER_STATE, "HTTP 404"))
                .willReturn(PENDING);

        assertThat(reconciliation.reconcileDue()).isEqualTo(2);

        verify(recording).stateUnavailable(FIRST);
        verify(recording).apply(SECOND, PENDING);
        assertThat(log.at(Level.WARN)).containsExactly("состояние заказа на оплату договора КМ " + FIRST
                + " не получено: сервис управления заказами не обработал заказ: order_state, HTTP 404");
    }

    @Test
    void orderWithoutOrderingNumber_isSkippedWithoutCalls() {
        PaymentOrder withoutNumber = PaymentOrders.awaiting();
        toReconcile(withoutNumber, orderOf(SECOND));

        assertThat(reconciliation.reconcileDue()).isEqualTo(2);

        verify(recording, never()).apply(PaymentOrders.CONTRACT, PENDING);
        verify(recording).apply(SECOND, PENDING);
    }

    @Test
    void standby_reconcilesNothing() {
        willReturn(0).given(leadership).countAsLeader(any());

        assertThat(reconciliation.reconcileDue()).isZero();

        verifyNoInteractions(orders, ordering, recording, issuing);
    }
}
