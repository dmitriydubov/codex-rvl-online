package ru.projectx.contracts.service.payment.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.Limit;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.entity.payment.PaymentOrderView;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingRejectedException;
import ru.projectx.contracts.exception.OrderingUnavailableException;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;
import ru.projectx.contracts.service.payment.CheckoutRequest;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.NewOrder;
import ru.projectx.contracts.service.payment.Ordering;
import ru.projectx.contracts.service.payment.OrderingAttempts;
import ru.projectx.contracts.service.payment.OrderingSession;
import ru.projectx.contracts.service.payment.OrderingStepRunner;
import ru.projectx.contracts.service.payment.PaymentOrderPasses;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.OrderingTestProperties;
import ru.projectx.contracts.support.PaymentOrders;
import ru.projectx.contracts.support.Tasks;

class InvoiceIssuingTest {

    private static final CheckoutRequest REQUEST = new CheckoutRequest(PaymentOrders.ORDER_ID,
            new NewOrder(Tasks.INSURED, PaymentOrders.ORDER, PaymentOrders.DOCUMENT), Invoices.DEADLINE,
            PaymentOrders.PAYER);
    private static final OrderingUnavailableException TIMEOUT =
            new OrderingUnavailableException(OrderingOperation.CHECKOUT, "нет ответа");

    private final PaymentOrderRepository orders = mock();
    private final BackgroundLeadership leadership = mock();
    private final Ordering ordering = mock();
    private final OrderingSession session = mock();
    private final InvoiceRecording recording = mock();
    private final OrderingStepRunner steps =
            new OrderingStepRunner(ordering, new OrderingAttempts(OrderingTestProperties.attempts(3)));
    private final InvoiceIssuing issuing = new InvoiceIssuing(
            new PaymentOrderPasses(orders, leadership, steps, OrderingTestProperties.passes()), steps, recording);
    private final PaymentOrderView order = PaymentOrders.created().view();
    private final CapturedLog log = CapturedLog.of(OrderingStepRunner.class);

    @BeforeEach
    void orderingSessionOfTheOrder() {
        given(ordering.session(PaymentOrders.CORRELATION)).willReturn(session);
        given(recording.start(PaymentOrders.CONTRACT)).willReturn(Optional.of(REQUEST));
    }

    @AfterEach
    void releaseLog() {
        log.close();
    }

    @Test
    void issueDue_takesCreatedOrdersWithReadyInvoicesInTheBatchOfThePass() {
        given(leadership.countAsLeader(any())).willAnswer(pass -> pass.<IntSupplier>getArgument(0).getAsInt());
        PaymentOrder first = PaymentOrders.created();
        given(orders.findWithInvoiceIn(PaymentOrderState.CREATED, InvoiceState.READY,
                Limit.of(OrderingTestProperties.BATCH))).willReturn(List.of(first, PaymentOrders.created()));
        given(session.checkout(REQUEST)).willReturn(Invoices.LINK);

        assertThat(issuing.issueDue()).isEqualTo(2);

        verify(recording, times(2)).issued(PaymentOrders.CONTRACT, Invoices.LINK);
    }

    @Test
    void issue_claimsTheCheckoutThenCallsOrderingAndRecordsTheLink() {
        given(session.checkout(REQUEST)).willReturn(Invoices.LINK);

        assertThat(issuing.issue(order)).isTrue();

        InOrder sequence = inOrder(recording, session);
        sequence.verify(recording).start(PaymentOrders.CONTRACT);
        sequence.verify(session).checkout(REQUEST);
        sequence.verify(recording).issued(PaymentOrders.CONTRACT, Invoices.LINK);
        verify(session, never()).orderStatus(any());
    }

    @Test
    void issue_ofOrderThatWasNotClaimed_callsNothingAndLetsThePassGoOn() {
        given(recording.start(PaymentOrders.CONTRACT)).willReturn(Optional.empty());

        assertThat(issuing.issue(order)).isTrue();

        verifyNoInteractions(ordering, session);
        verify(recording, never()).issued(any(), any());
    }

    @Test
    void failedCall_isRepeatedAfterTheOrderStateSaysTheOrderIsStillReady() {
        given(session.checkout(REQUEST)).willThrow(TIMEOUT).willReturn(Invoices.LINK);
        given(session.orderStatus(PaymentOrders.ORDER_ID)).willReturn("READY_TO_PAYMENT");

        assertThat(issuing.issue(order)).isTrue();

        verify(session, times(2)).checkout(REQUEST);
        verify(recording).issued(PaymentOrders.CONTRACT, Invoices.LINK);
        verify(recording, times(1)).start(PaymentOrders.CONTRACT);
    }

    @Test
    void failedCall_whoseOrderAlreadyAwaitsPayment_isRecordedAsLostReply() {
        given(session.checkout(REQUEST)).willThrow(TIMEOUT);
        given(session.orderStatus(PaymentOrders.ORDER_ID)).willReturn("PENDING_PAYMENT");

        assertThat(issuing.issue(order)).isTrue();

        verify(session, times(1)).checkout(REQUEST);
        verify(recording).replyLost(PaymentOrders.CONTRACT);
        verify(recording, never()).issued(any(), any());
    }

    @Test
    void rejection_isRecordedWithItsCodeWithoutRetries() {
        given(session.checkout(REQUEST))
                .willThrow(new OrderingRejectedException(OrderingOperation.CHECKOUT, "CheckoutValidatorService.valid"));

        assertThat(issuing.issue(order)).isTrue();

        verify(session, times(1)).checkout(REQUEST);
        verify(recording).rejected(PaymentOrders.CONTRACT, "CheckoutValidatorService.valid");
        verify(recording, never()).issued(any(), any());
        verify(recording, never()).replyLost(any());
    }

    @Test
    void orderingUnavailableForAllAttempts_recordsNothingAndStopsThePass() {
        given(session.checkout(REQUEST)).willThrow(TIMEOUT);
        given(session.orderStatus(PaymentOrders.ORDER_ID)).willReturn("READY_TO_PAYMENT");

        assertThat(issuing.issue(order)).isFalse();

        verify(session, times(3)).checkout(REQUEST);
        verify(recording, never()).issued(any(), any());
        verify(recording, never()).replyLost(any());
        verify(recording, never()).rejected(any(), any());
        assertThat(log.at(Level.WARN)).containsExactly("заказ на оплату договора КМ " + PaymentOrders.CONTRACT
                + " счёт не выставлен: сервис управления заказами недоступен: checkout, нет ответа,"
                + " проход остановлен до следующего раза");
    }

    @Test
    void orderInUnexpectedStateAfterFailedCall_waitsForTheNextPassWithoutStoppingThisOne() {
        given(session.checkout(REQUEST)).willThrow(TIMEOUT);
        given(session.orderStatus(PaymentOrders.ORDER_ID)).willReturn("CANCELED");

        assertThat(issuing.issue(order)).isTrue();

        verify(session, times(1)).checkout(REQUEST);
        verify(recording, never()).replyLost(any());
        assertThat(log.at(Level.WARN)).singleElement().asString()
                .endsWith("после сбоя выставления счёта заказ в состоянии CANCELED, заказ ждёт следующего прохода");
    }

    @Test
    void standby_issuesNothing() {
        willReturn(0).given(leadership).countAsLeader(any());

        assertThat(issuing.issueDue()).isZero();

        verifyNoInteractions(orders, ordering, recording);
    }
}
