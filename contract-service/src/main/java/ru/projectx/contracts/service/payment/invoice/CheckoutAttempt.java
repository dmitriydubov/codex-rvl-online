package ru.projectx.contracts.service.payment.invoice;

import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingOrderFailureException;
import ru.projectx.contracts.service.payment.CheckoutRequest;
import ru.projectx.contracts.service.payment.OrderingSession;
import ru.projectx.contracts.service.payment.OrderingStatuses;

final class CheckoutAttempt {

    private final CheckoutRequest request;
    private boolean sent;

    CheckoutAttempt(CheckoutRequest request) {
        this.request = request;
    }

    CheckoutOutcome apply(OrderingSession session) {
        if (sent) {
            return afterFailedCall(session);
        }
        return send(session);
    }

    private CheckoutOutcome send(OrderingSession session) {
        sent = true;
        return new CheckoutOutcome.Issued(session.checkout(request));
    }

    private CheckoutOutcome afterFailedCall(OrderingSession session) {
        String status = session.orderStatus(request.orderId());
        if (OrderingStatuses.READY_TO_PAYMENT.equals(status)) {
            return send(session);
        }
        if (OrderingStatuses.PENDING_PAYMENT.equals(status)) {
            return new CheckoutOutcome.ReplyLost();
        }
        throw new OrderingOrderFailureException(OrderingOperation.CHECKOUT,
                "после сбоя выставления счёта заказ в состоянии " + status);
    }
}
