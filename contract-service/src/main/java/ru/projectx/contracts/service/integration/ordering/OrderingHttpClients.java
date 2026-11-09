package ru.projectx.contracts.service.integration.ordering;

import java.time.Clock;
import org.springframework.web.client.RestClient;
import ru.projectx.contracts.config.payment.OrderingProperties;
import ru.projectx.contracts.service.payment.OrderingMetrics;
import tools.jackson.databind.json.JsonMapper;

final class OrderingHttpClients {

    private OrderingHttpClients() {
    }

    static OrderingHttpClient create(OrderingProperties settings, JsonMapper json, OrderingMetrics metrics,
            Clock clock) {
        OrderingTokens tokens = OrderingTokenSources.create(settings.token(), clock);
        RestClient http = RestClient.builder()
                .baseUrl(settings.url())
                .requestFactory(OrderingRestClients.requests(settings.connectTimeout(), settings.readTimeout()))
                .build();
        OrderingCalls calls = new OrderingCalls(new OrderingExchange(http, json), new OrderingRequests(json, settings),
                tokens, metrics);
        return new OrderingHttpClient(tokens, calls, metrics);
    }
}
