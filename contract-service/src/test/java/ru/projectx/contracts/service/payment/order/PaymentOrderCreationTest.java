package ru.projectx.contracts.service.payment.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.MDC;
import org.springframework.data.domain.Limit;
import ru.projectx.contracts.entity.payment.OrderRequest;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingRejectedException;
import ru.projectx.contracts.exception.OrderingUnavailableException;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;
import ru.projectx.contracts.service.payment.NewOrder;
import ru.projectx.contracts.service.payment.Ordering;
import ru.projectx.contracts.service.payment.OrderingAttempts;
import ru.projectx.contracts.service.payment.OrderingStepRunner;
import ru.projectx.contracts.service.payment.OrderingSession;
import ru.projectx.contracts.service.payment.PaymentOrderPasses;
import ru.projectx.contracts.service.payment.invoice.PaymentTerms;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.OrderingTestProperties;
import ru.projectx.contracts.support.PaymentOrders;
import ru.projectx.contracts.support.Tasks;

class PaymentOrderCreationTest {

    private static final NewOrder NEW_ORDER =
            new NewOrder(Tasks.INSURED, PaymentOrders.ORDER, PaymentOrders.DOCUMENT);
    private static final OrderingUnavailableException TIMEOUT =
            new OrderingUnavailableException(OrderingOperation.CREATE_ORDER, "нет ответа");
    private static final Instant BOUNDARY = PaymentOrders.DOCUMENT.documentTtl();

    private final PaymentOrderRepository orders = mock();
    private final Ordering ordering = mock();
    private final OrderingSession session = mock();
    private final PaymentOrderRecording recording = mock();
    private final BackgroundLeadership leadership = mock();
    private final OrderingStepRunner steps =
            new OrderingStepRunner(ordering, new OrderingAttempts(OrderingTestProperties.attempts(3)));
    private final PaymentOrderCreation creation = creationAt(BOUNDARY.minus(Duration.ofDays(30)));

    private final CapturedLog log = CapturedLog.of(OrderingStepRunner.class);

    @org.junit.jupiter.api.AfterEach
    void releaseLog() {
        log.close();
    }

    @BeforeEach
    void leaderWithOneAwaitingOrder() {
        given(leadership.countAsLeader(any())).willAnswer(pass -> pass.<IntSupplier>getArgument(0).getAsInt());
        given(ordering.session(PaymentOrders.CORRELATION)).willReturn(session);
        given(recording.requestCreation(PaymentOrders.CONTRACT)).willReturn(CreationClaim.FIRST);
        awaiting(PaymentOrders.awaiting());
    }

    @Test
    void firstAttempt_marksTheRequestThenCreatesWithoutSearchingAndRecordsTheOrder() {
        given(session.createOrder(NEW_ORDER)).willReturn(PaymentOrders.ORDER_ID);

        assertThat(creation.createDue()).isEqualTo(1);

        InOrder order = inOrder(recording, session);
        order.verify(recording).requestCreation(PaymentOrders.CONTRACT);
        order.verify(session).createOrder(NEW_ORDER);
        order.verify(recording).created(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID);
        verify(session, never()).findOrder(any(), any());
        verify(orders).findByStateOrderByCreatedAtAscIdAsc(PaymentOrderState.AWAITING_CREATION, Limit.of(7));
    }

    @Test
    void lostAnswer_isFollowedBySearchThatFindsTheOrderWithoutSecondCreation() {
        given(session.createOrder(NEW_ORDER)).willThrow(TIMEOUT);
        given(session.findOrder(Tasks.INSURED, PaymentOrders.ORDER)).willReturn(Optional.of(PaymentOrders.ORDER_ID));

        creation.createDue();

        verify(session, times(1)).createOrder(NEW_ORDER);
        verify(recording).created(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID);
    }

    @Test
    void temporaryFailures_searchBeforeEveryRetryAndLeaveTheOrderWaiting() {
        given(session.createOrder(NEW_ORDER)).willThrow(TIMEOUT);
        given(session.findOrder(Tasks.INSURED, PaymentOrders.ORDER)).willReturn(Optional.empty());

        creation.createDue();

        verify(session, times(3)).createOrder(NEW_ORDER);
        verify(session, times(2)).findOrder(Tasks.INSURED, PaymentOrders.ORDER);
        assertThat(log.at(ch.qos.logback.classic.Level.WARN)).containsExactly("заказ на оплату договора КМ "
                + PaymentOrders.CONTRACT + " не создан: сервис управления заказами недоступен: create_order, нет ответа,"
                + " проход остановлен до следующего раза");
        verify(recording, never()).created(any(), any());
        verify(recording, never()).rejected(any(), any());
    }

    @Test
    void orderWhoseCreationWasSentEarlier_startsWithSearch() {
        PaymentOrder sent = PaymentOrders.awaiting();
        sent.markRequested(OrderRequest.CREATION, PaymentOrders.DOCUMENT.documentDate());
        awaiting(sent);
        given(session.findOrder(Tasks.INSURED, PaymentOrders.ORDER)).willReturn(Optional.empty());
        given(session.createOrder(NEW_ORDER)).willReturn(PaymentOrders.ORDER_ID);
        given(recording.requestCreation(PaymentOrders.CONTRACT)).willReturn(CreationClaim.REPEATED);

        creation.createDue();

        InOrder order = inOrder(session);
        order.verify(session).findOrder(Tasks.INSURED, PaymentOrders.ORDER);
        order.verify(session).createOrder(NEW_ORDER);
    }

    @Test
    void creationClaimedByAnotherPass_isNotSentBlindButSearchedOnTheNextAttempt() {
        given(recording.requestCreation(PaymentOrders.CONTRACT)).willReturn(CreationClaim.REPEATED);
        given(session.findOrder(Tasks.INSURED, PaymentOrders.ORDER)).willReturn(Optional.of(PaymentOrders.ORDER_ID));

        creation.createDue();

        verify(session, never()).createOrder(any());
        verify(recording).created(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID);
    }

    @Test
    void creationClaimedByAnotherPass_andStillNotFound_isSentOnlyAfterTheSearch() {
        given(recording.requestCreation(PaymentOrders.CONTRACT)).willReturn(CreationClaim.REPEATED);
        given(session.findOrder(Tasks.INSURED, PaymentOrders.ORDER)).willReturn(Optional.empty());
        given(session.createOrder(NEW_ORDER)).willReturn(PaymentOrders.ORDER_ID);

        creation.createDue();

        InOrder order = inOrder(session);
        order.verify(session).findOrder(Tasks.INSURED, PaymentOrders.ORDER);
        order.verify(session).createOrder(NEW_ORDER);
        verify(session, times(1)).createOrder(NEW_ORDER);
    }

    @Test
    void rejection_isRecordedWithItsCodeWithoutRetries() {
        given(session.createOrder(NEW_ORDER))
                .willThrow(new OrderingRejectedException(OrderingOperation.CREATE_ORDER, "CreateOrderValidatorService.valid"));

        creation.createDue();

        verify(session, times(1)).createOrder(NEW_ORDER);
        verify(recording).rejected(PaymentOrders.CONTRACT, "CreateOrderValidatorService.valid");
    }

    @Test
    void tokenFailure_sendsNothingAndDoesNotMarkTheRequest() {
        given(ordering.session(PaymentOrders.CORRELATION))
                .willThrow(new OrderingUnavailableException(OrderingOperation.TOKEN, "HTTP 503"));

        creation.createDue();

        verify(ordering, times(3)).session(PaymentOrders.CORRELATION);
        verify(recording, never()).requestCreation(any());
        verifyNoInteractions(session);
    }

    @Test
    void orderWithdrawnBeforeTheRequest_isNotCreated() {
        given(recording.requestCreation(PaymentOrders.CONTRACT)).willReturn(CreationClaim.WITHDRAWN);

        creation.createDue();

        verify(session, never()).createOrder(any());
        verify(recording, never()).created(any(), any());
    }

    @Test
    void correlationId_isInTheLogContextDuringTheCallAndRemovedAfter() {
        given(session.createOrder(NEW_ORDER)).willAnswer(call -> {
            assertThat(MDC.get("correlationId")).isEqualTo(PaymentOrders.CORRELATION.toString());
            return PaymentOrders.ORDER_ID;
        });

        creation.createDue();

        verify(session).createOrder(NEW_ORDER);
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void standby_createsNothing() {
        org.mockito.BDDMockito.willReturn(0).given(leadership).countAsLeader(any());

        assertThat(creation.createDue()).isZero();

        verifyNoInteractions(orders, ordering, recording);
    }

    @Test
    void orderPastTheBoundaryBeforeCheckIn_isClosedWithoutCallingOrderingOrTheTokenServer() {
        PaymentOrderCreation late = creationAt(BOUNDARY.plusNanos(1));

        assertThat(late.createDue()).isEqualTo(1);

        verify(recording).expired(PaymentOrders.CONTRACT);
        verify(recording, never()).requestCreation(any());
        verifyNoInteractions(ordering, session);
    }

    @Test
    void orderClosedByTheBoundary_doesNotStopThePass() {
        awaiting(PaymentOrders.awaiting(), PaymentOrders.awaiting());
        PaymentOrderCreation late = creationAt(BOUNDARY.plusSeconds(1));

        assertThat(late.createDue()).isEqualTo(2);

        verify(recording, times(2)).expired(PaymentOrders.CONTRACT);
    }

    @Test
    void orderExactlyOnTheBoundary_isStillCreated() {
        given(session.createOrder(NEW_ORDER)).willReturn(PaymentOrders.ORDER_ID);
        PaymentOrderCreation onTheBoundary = creationAt(BOUNDARY);

        onTheBoundary.createDue();

        verify(recording, never()).expired(any());
        verify(recording).created(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID);
    }

    @Test
    void createdOrder_letsThePassGoOnToTheNextOne() {
        awaiting(PaymentOrders.awaiting(), PaymentOrders.awaiting());
        given(session.createOrder(NEW_ORDER)).willReturn(PaymentOrders.ORDER_ID);

        assertThat(creation.createDue()).isEqualTo(2);

        verify(recording, times(2)).created(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID);
    }

    @Test
    void orderingUnavailableForAllAttempts_stopsThePassBeforeTheNextOrder() {
        awaiting(PaymentOrders.awaiting(), PaymentOrders.awaiting());
        given(session.createOrder(NEW_ORDER)).willThrow(TIMEOUT);
        given(session.findOrder(Tasks.INSURED, PaymentOrders.ORDER)).willReturn(Optional.empty());

        assertThat(creation.createDue()).isEqualTo(1);

        verify(session, times(3)).createOrder(NEW_ORDER);
    }

    @Test
    void creationPass_takesTheOrdersAwaitingCreation() {
        assertThat(creation.state()).isEqualTo(PaymentOrderState.AWAITING_CREATION);
    }

    private PaymentOrderCreation creationAt(Instant now) {
        PaymentOrderPasses passes = new PaymentOrderPasses(orders, leadership, steps, OrderingTestProperties.passes());
        return new PaymentOrderCreation(passes, recording, steps, new PaymentTerms(OrderingTestProperties.passes()),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private void awaiting(PaymentOrder... due) {
        given(orders.findByStateOrderByCreatedAtAscIdAsc(PaymentOrderState.AWAITING_CREATION, Limit.of(7)))
                .willReturn(List.of(due));
    }
}
