package ru.projectx.contracts.service.integration.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.payment.OrderDocument;
import ru.projectx.contracts.exception.OrderingUnavailableException;
import ru.projectx.contracts.service.payment.NewOrder;
import ru.projectx.contracts.service.payment.OrderingMetrics;
import tools.jackson.databind.json.JsonMapper;

class OrderingExchangeTest {

    private static final UUID CORRELATION = UUID.fromString("0196f0a2-0000-7000-8000-0000000000ab");
    private static final UUID CLIENT = UUID.fromString("0196f0a2-0000-7000-8000-0000000000c1");
    private static final UUID DOCUMENT = UUID.fromString("0196f0a2-0000-7000-8000-000000000012");
    private static final NewOrder ORDER = new NewOrder(CLIENT, DOCUMENT, new OrderDocument("DKM-2026-000002",
            new BigDecimal("1200.00"), Instant.parse("2026-02-10T07:04:58Z"), Instant.parse("2026-04-09T20:59:59Z")));
    private static final String CREATED_HEAD = "{\"status\": \"success\", \"orderId\": \"o-big\", \"pad\": \"";
    private static final String CREATED_TAIL = "\"}";

    private OrderingTestServer server;
    private OrderingHttpClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = new OrderingTestServer();
        client = OrderingHttpClients.create(OrderingSettings.at(server.orderingUrl(), server.tokenUri()),
                JsonMapper.builder().build(), new OrderingMetrics(new SimpleMeterRegistry()),
                new MovableClock(Instant.parse("2026-02-10T07:05:00Z")));
        server.token(OrderingTestServer.accessToken("tok-x", 600));
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void everyCall_asksForAJsonAnswer() {
        server.ordering(OrderingTestServer.json(200, "{\"status\": \"success\", \"orderId\": \"o-1\"}"));

        client.session(CORRELATION).createOrder(ORDER);

        assertThat(server.orderingRequests().getFirst().header("Accept")).isEqualTo("application/json");
    }

    @Test
    void answerOfExactlyTheBodyLimit_isRead() {
        server.ordering(OrderingTestServer.json(200, createdOfSize(OrderingExchange.MAX_BODY_BYTES)));

        assertThat(client.session(CORRELATION).createOrder(ORDER)).isEqualTo("o-big");
    }

    @Test
    void answerOverTheBodyLimit_isNotReadAndIsTemporary() {
        server.ordering(OrderingTestServer.json(200, createdOfSize(OrderingExchange.MAX_BODY_BYTES + 1)));

        assertThatThrownBy(() -> client.session(CORRELATION).createOrder(ORDER))
                .isInstanceOf(OrderingUnavailableException.class)
                .hasMessageContaining("ответ не по формату");
    }

    @Test
    void emptySuccessAnswer_isNotByTheFormatAndIsTemporary() {
        server.ordering(OrderingTestServer.json(200, ""));

        assertThatThrownBy(() -> client.session(CORRELATION).createOrder(ORDER))
                .isInstanceOf(OrderingUnavailableException.class)
                .hasMessageContaining("ответ не по формату: HTTP 200");
    }

    private static String createdOfSize(int bytes) {
        return CREATED_HEAD + "x".repeat(bytes - CREATED_HEAD.length() - CREATED_TAIL.length()) + CREATED_TAIL;
    }
}
