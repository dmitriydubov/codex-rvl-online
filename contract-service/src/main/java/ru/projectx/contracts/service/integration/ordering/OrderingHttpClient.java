package ru.projectx.contracts.service.integration.ordering;

import java.util.UUID;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingUnavailableException;
import ru.projectx.contracts.service.payment.Ordering;
import ru.projectx.contracts.service.payment.OrderingMetrics;
import ru.projectx.contracts.service.payment.OrderingSession;

public final class OrderingHttpClient implements Ordering {

    private final OrderingTokens tokens;
    private final OrderingCalls calls;
    private final OrderingMetrics metrics;

    OrderingHttpClient(OrderingTokens tokens, OrderingCalls calls, OrderingMetrics metrics) {
        this.tokens = tokens;
        this.calls = calls;
        this.metrics = metrics;
    }

    @Override
    public OrderingSession session(UUID correlationId) {
        return new OrderingHttpSession(calls, accessToken(), correlationId);
    }

    private String accessToken() {
        try {
            return tokens.accessToken();
        }
        catch (OrderingUnavailableException unavailable) {
            metrics.countFailure(OrderingOperation.TOKEN);
            throw unavailable;
        }
    }
}
