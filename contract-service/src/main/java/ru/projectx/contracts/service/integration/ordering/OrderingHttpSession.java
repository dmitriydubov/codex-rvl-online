package ru.projectx.contracts.service.integration.ordering;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.service.integration.ordering.OrderingExchange.OrderingCall;
import ru.projectx.contracts.service.payment.NewOrder;
import ru.projectx.contracts.service.payment.OrderingSession;

final class OrderingHttpSession implements OrderingSession {

    private static final String ORDERS = "/orders";
    private static final String CLIENT_ORDERS = "/orders/{clientId}";
    private static final String ORDER = "/orders/{orderId}";
    private static final String ORDER_CANCEL = "/orders/{orderId}/cancel";
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
        return calls.call(call, exchange -> exchange.post(call, CLIENT_ORDERS, requests.find(document), clientId),
                body -> OrderingSearchResults.orderId(body, requests.product(), document));
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
        return calls.call(call, exchange -> exchange.get(call, ORDER, orderId),
                body -> OrderingReplies.requiredText(call.operation(), body.path(DATA).path(STATUS)));
    }

    @Override
    public void cancelOrder(String orderId) {
        OrderingCall call = call(OrderingOperation.CANCEL_ORDER);
        calls.call(call, exchange -> exchange.post(call, ORDER_CANCEL, requests.cancel(), orderId), Function.identity());
    }

    private OrderingCall call(OrderingOperation operation) {
        return new OrderingCall(operation, token, correlationId);
    }
}
