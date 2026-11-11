package ru.projectx.contracts.service.integration.ordering;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingOrderFailureException;
import ru.projectx.contracts.exception.OrderingRejectedException;
import ru.projectx.contracts.service.integration.ordering.OrderingExchange.OrderingCall;
import ru.projectx.contracts.entity.payment.InvoiceLink;
import ru.projectx.contracts.service.payment.CheckoutRequest;
import ru.projectx.contracts.service.payment.NewOrder;
import ru.projectx.contracts.service.payment.OrderingOrder;
import ru.projectx.contracts.service.payment.OrderingSession;

final class OrderingHttpSession implements OrderingSession {

    private static final String ORDERS = "/orders";
    private static final String CLIENT_ORDERS = "/orders/{clientId}";
    private static final String ORDER = "/orders/{orderId}";
    private static final String ORDER_CANCEL = "/orders/{orderId}/cancel";
    private static final String ORDER_CHECKOUT = "/orders/{orderId}/checkout";
    private static final String INVOICE_ID = "invoiceId";
    private static final String PAYMENT_URL = "paymentUrl";
    private static final String ORDER_ID = "orderId";
    private static final String DATA = "data";
    private static final String STATUS = "status";

    private final OrderingCalls calls;
    private final OrderingRequests requests;
    private final String token;
    private final UUID correlationId;

    OrderingHttpSession(OrderingCalls calls, String token, UUID correlationId) {
        this.calls = calls;
        this.requests = calls.requests();
        this.token = token;
        this.correlationId = correlationId;
    }

    @Override
    public Optional<String> findOrder(UUID clientId, UUID documentId) {
        OrderingCall call = call(OrderingOperation.FIND_ORDERS);
        String document = documentId.toString();
        return orderScoped(call, () -> calls.call(call,
                exchange -> exchange.post(call, CLIENT_ORDERS, requests.find(document), clientId),
                body -> OrderingSearchResults.orderId(body, requests.product(), document)));
    }

    @Override
    public String createOrder(NewOrder order) {
        OrderingCall call = call(OrderingOperation.CREATE_ORDER);
        return calls.call(call, exchange -> exchange.post(call, ORDERS, requests.create(order)),
                body -> OrderingReplies.requiredText(call.operation(), body.path(ORDER_ID)));
    }

    @Override
    public String orderStatus(String orderId) {
        OrderingCall call = call(OrderingOperation.ORDER_STATE);
        return orderScoped(call, () -> calls.call(call, exchange -> exchange.get(call, ORDER, orderId),
                body -> OrderingReplies.requiredText(call.operation(), body.path(DATA).path(STATUS))));
    }

    @Override
    public void cancelOrder(String orderId) {
        OrderingCall call = call(OrderingOperation.CANCEL_ORDER);
        calls.call(call, exchange -> exchange.post(call, ORDER_CANCEL, requests.cancel(), orderId), Function.identity());
    }

    @Override
    public InvoiceLink checkout(CheckoutRequest request) {
        OrderingCall call = call(OrderingOperation.CHECKOUT);
        return calls.call(call,
                exchange -> exchange.post(call, ORDER_CHECKOUT, requests.checkout(request), request.orderId()),
                body -> new InvoiceLink(OrderingReplies.requiredText(call.operation(), body.path(INVOICE_ID)),
                        OrderingReplies.requiredUrl(call.operation(), body.path(PAYMENT_URL))));
    }

    @Override
    public OrderingOrder order(String orderId) {
        OrderingCall call = call(OrderingOperation.ORDER_STATE);
        return orderScoped(call, () -> calls.call(call, exchange -> exchange.get(call, ORDER, orderId),
                body -> OrderingOrders.read(body.path(DATA))));
    }

    private static <T> T orderScoped(OrderingCall call, Supplier<T> request) {
        try {
            return request.get();
        }
        catch (OrderingRejectedException rejected) {
            throw new OrderingOrderFailureException(call.operation(), "запрос отклонён, код " + rejected.code(),
                    rejected);
        }
    }

    private OrderingCall call(OrderingOperation operation) {
        return new OrderingCall(operation, token, correlationId);
    }
}
