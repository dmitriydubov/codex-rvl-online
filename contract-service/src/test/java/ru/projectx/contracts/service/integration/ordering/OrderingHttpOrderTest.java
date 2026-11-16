package ru.projectx.contracts.service.integration.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingOrderFailureException;
import ru.projectx.contracts.exception.OrderingUnavailableException;
import ru.projectx.contracts.service.integration.ordering.OrderingTestServer.Received;
import ru.projectx.contracts.service.payment.OrderingEvent;
import ru.projectx.contracts.service.payment.OrderingMetrics;
import ru.projectx.contracts.service.payment.OrderingOrder;
import tools.jackson.databind.json.JsonMapper;

class OrderingHttpOrderTest {

    private static final UUID CORRELATION = UUID.fromString("0196f0a2-0000-7000-8000-0000000000aa");
    private static final String ORDER_ID = "0196f0a2-5b1c-7d3e-9f40-1a2b3c4d5e01";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private OrderingTestServer server;
    private OrderingHttpClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = new OrderingTestServer();
        client = OrderingHttpClients.create(OrderingSettings.at(server.orderingUrl(), server.tokenUri()),
                JsonMapper.builder().build(), new OrderingMetrics(registry),
                new MovableClock(Instant.parse("2026-02-10T07:05:00Z")));
        server.token(OrderingTestServer.accessToken("tok-1", 600));
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void order_isReadWithItsStatusAndTheHistoryOfItsEvents() {
        server.ordering(OrderingTestServer.json(200, """
                {"status": "success", "data": {"status": "PAID", "ttl": "2026-04-09T23:59:59+03:00", "events": [
                 {"eventType": "pendingPayment", "orderStatus": "PENDING_PAYMENT", "created": "2026-02-10T10:05:01+03:00"},
                 {"eventType": "paid", "orderStatus": "PAID", "created": "2026-02-10T10:20:15+03:00"}]}}"""));

        OrderingOrder order = client.session(CORRELATION).order(ORDER_ID);

        assertThat(order).isEqualTo(new OrderingOrder("PAID", List.of(
                new OrderingEvent("pendingPayment", Instant.parse("2026-02-10T07:05:01Z")),
                new OrderingEvent("paid", Instant.parse("2026-02-10T07:20:15Z")))));
        Received request = server.orderingRequests().getFirst();
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.path()).isEqualTo("/ordering/orders/" + ORDER_ID);
        assertThat(request.header("Authorization")).isEqualTo("Bearer tok-1");
        assertThat(request.header("correlationId")).isEqualTo(CORRELATION.toString());
    }

    @Test
    void orderWithoutStatus_isAFailureOfThisOrderCountedForItsState() {
        server.ordering(OrderingTestServer.json(200, "{\"status\": \"success\", \"data\": {\"events\": []}}"));

        assertThatThrownBy(() -> client.session(CORRELATION).order(ORDER_ID))
                .isInstanceOfSatisfying(OrderingOrderFailureException.class, failure -> {
                    assertThat(failure.operation()).isEqualTo(OrderingOperation.ORDER_STATE);
                    assertThat(failure.serviceWide()).isFalse();
                });
        assertThat(failures()).isEqualTo(1);
    }

    @Test
    void rejectedOrderRequest_isAFailureOfThisOrderNamingTheCode() {
        server.ordering(OrderingTestServer.json(404, "{\"status\": \"error\", \"errors\": [{\"code\": \"OrderNotFound\"}]}"));

        assertThatThrownBy(() -> client.session(CORRELATION).order(ORDER_ID))
                .isInstanceOfSatisfying(OrderingOrderFailureException.class, failure -> {
                    assertThat(failure.operation()).isEqualTo(OrderingOperation.ORDER_STATE);
                    assertThat(failure.serviceWide()).isFalse();
                })
                .hasMessageContaining("запрос отклонён, код OrderNotFound");
        assertThat(failures()).isEqualTo(1);
    }

    @Test
    void unavailableOrderingAtTheOrderRequest_isATemporaryFailureOfTheWholeService() {
        server.ordering(OrderingTestServer.json(503, ""));

        assertThatThrownBy(() -> client.session(CORRELATION).order(ORDER_ID))
                .isInstanceOfSatisfying(OrderingUnavailableException.class,
                        unavailable -> assertThat(unavailable.serviceWide()).isTrue());
    }

    private double failures() {
        return registry.get("contracts.ordering.failures").tag("operation", "order_state").counter().count();
    }
}
