package ru.projectx.contracts.service.payment.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingOrderFailureException;
import ru.projectx.contracts.exception.OrderingUnavailableException;
import ru.projectx.contracts.service.payment.CheckoutRequest;
import ru.projectx.contracts.service.payment.Invoices;
import ru.projectx.contracts.service.payment.NewOrder;
import ru.projectx.contracts.service.payment.OrderingSession;
import ru.projectx.contracts.support.PaymentOrders;
import ru.projectx.contracts.support.Tasks;

class CheckoutAttemptTest {

    private static final CheckoutRequest REQUEST = new CheckoutRequest(PaymentOrders.ORDER_ID,
            new NewOrder(Tasks.INSURED, PaymentOrders.ORDER, PaymentOrders.DOCUMENT), Invoices.DEADLINE,
            PaymentOrders.PAYER);
    private static final OrderingUnavailableException TIMEOUT =
            new OrderingUnavailableException(OrderingOperation.CHECKOUT, "нет ответа");

    private final OrderingSession session = mock();
    private final CheckoutAttempt attempt = new CheckoutAttempt(REQUEST);

    private void failTheFirstCall() {
        given(session.checkout(REQUEST)).willThrow(TIMEOUT);
        assertThatThrownBy(() -> attempt.apply(session)).isSameAs(TIMEOUT);
    }

    @Test
    void firstAttempt_sendsTheCheckoutWithoutAskingTheOrderState() {
        given(session.checkout(REQUEST)).willReturn(Invoices.LINK);

        assertThat(attempt.apply(session)).isEqualTo(new CheckoutOutcome.Issued(Invoices.LINK));

        verify(session, never()).orderStatus(any());
    }

    @Test
    void repeatAfterFailedCall_startsWithTheOrderStateAndSendsAgainWhileTheOrderIsStillReady() {
        failTheFirstCall();
        given(session.orderStatus(PaymentOrders.ORDER_ID)).willReturn("READY_TO_PAYMENT");
        org.mockito.BDDMockito.willReturn(Invoices.LINK).given(session).checkout(REQUEST);

        assertThat(attempt.apply(session)).isEqualTo(new CheckoutOutcome.Issued(Invoices.LINK));

        InOrder sequence = inOrder(session);
        sequence.verify(session).checkout(REQUEST);
        sequence.verify(session).orderStatus(PaymentOrders.ORDER_ID);
        sequence.verify(session).checkout(REQUEST);
    }

    @Test
    void repeatAfterFailedCall_admitsTheReplyLostWhenTheOrderAlreadyAwaitsPayment() {
        failTheFirstCall();
        given(session.orderStatus(PaymentOrders.ORDER_ID)).willReturn("PENDING_PAYMENT");

        assertThat(attempt.apply(session)).isEqualTo(new CheckoutOutcome.ReplyLost());

        verify(session, times(1)).checkout(REQUEST);
    }

    @Test
    void repeatAfterFailedCall_failsThisOrderOnlyWhenItsStateFitsNeitherCase() {
        failTheFirstCall();
        given(session.orderStatus(PaymentOrders.ORDER_ID)).willReturn("PAID");

        OrderingOrderFailureException failure =
                catchThrowableOfType(OrderingOrderFailureException.class, () -> attempt.apply(session));

        assertThat(failure).hasMessage("сервис управления заказами не обработал заказ: checkout, "
                + "после сбоя выставления счёта заказ в состоянии PAID");
        assertThat(failure.operation()).isEqualTo(OrderingOperation.CHECKOUT);
        assertThat(failure.serviceWide()).isFalse();
        verify(session, times(1)).checkout(REQUEST);
    }

    @Test
    void everyFurtherRepeat_asksTheOrderStateAgain() {
        failTheFirstCall();
        given(session.orderStatus(PaymentOrders.ORDER_ID)).willReturn("READY_TO_PAYMENT");
        assertThatThrownBy(() -> attempt.apply(session)).isSameAs(TIMEOUT);

        assertThatThrownBy(() -> attempt.apply(session)).isSameAs(TIMEOUT);

        verify(session, times(2)).orderStatus(PaymentOrders.ORDER_ID);
        verify(session, times(3)).checkout(REQUEST);
    }
}
