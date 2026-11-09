package ru.projectx.contracts.service.integration.ordering;

import java.util.function.Function;
import ru.projectx.contracts.exception.OrderingRejectedException;
import ru.projectx.contracts.exception.OrderingUnavailableException;
import ru.projectx.contracts.service.integration.ordering.OrderingExchange.OrderingCall;
import ru.projectx.contracts.service.payment.OrderingMetrics;
import tools.jackson.databind.JsonNode;

final class OrderingCalls {

    private final OrderingExchange exchange;
    private final OrderingRequests requests;
    private final OrderingTokens tokens;
    private final OrderingMetrics metrics;

    OrderingCalls(OrderingExchange exchange, OrderingRequests requests, OrderingTokens tokens,
            OrderingMetrics metrics) {
        this.exchange = exchange;
        this.requests = requests;
        this.tokens = tokens;
        this.metrics = metrics;
    }

    OrderingRequests requests() {
        return requests;
    }

    <T> T call(OrderingCall call, Function<OrderingExchange, OrderingReply> send, Function<JsonNode, T> read) {
        try {
            return read.apply(OrderingReplies.successBody(call.operation(), answered(send)));
        }
        catch (OrderingUnavailableException | OrderingRejectedException failure) {
            metrics.countFailure(call.operation());
            throw failure;
        }
    }

    private OrderingReply answered(Function<OrderingExchange, OrderingReply> send) {
        OrderingReply reply = send.apply(exchange);
        if (OrderingReplies.tokenRefused(reply)) {
            tokens.discard();
        }
        return reply;
    }
}
