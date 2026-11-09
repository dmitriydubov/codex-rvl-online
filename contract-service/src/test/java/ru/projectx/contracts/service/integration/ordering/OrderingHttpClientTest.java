package ru.projectx.contracts.service.integration.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.payment.OrderDocument;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingRejectedException;
import ru.projectx.contracts.exception.OrderingUnavailableException;
import ru.projectx.contracts.service.integration.ordering.OrderingTestServer.Received;
import ru.projectx.contracts.service.payment.NewOrder;
import ru.projectx.contracts.service.payment.OrderingMetrics;
import ru.projectx.contracts.service.payment.OrderingSession;
import tools.jackson.databind.json.JsonMapper;

class OrderingHttpClientTest {

    private static final UUID CORRELATION = UUID.fromString("0196f0a2-0000-7000-8000-0000000000aa");
    private static final UUID CLIENT = UUID.fromString("ae8312c3-350b-4d0b-806c-5e2460e74dbc");
    private static final UUID DOCUMENT = UUID.fromString("0196f0a2-0000-7000-8000-000000000011");
    private static final NewOrder ORDER = new NewOrder(CLIENT, DOCUMENT, new OrderDocument("DKM-2026-000001",
            new BigDecimal("34000.00"), Instant.parse("2026-02-10T07:04:58Z"), Instant.parse("2026-04-09T20:59:59Z")));
    private static final String CREATED = "{\"status\": \"success\", \"orderId\": \"o-1\"}";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MovableClock clock = new MovableClock(Instant.parse("2026-02-10T07:05:00Z"));
    private OrderingTestServer server;
    private OrderingHttpClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = new OrderingTestServer();
        client = OrderingHttpClients.create(OrderingSettings.at(server.orderingUrl(), server.tokenUri()),
                JsonMapper.builder().build(), new OrderingMetrics(registry), clock);
        server.token(OrderingTestServer.accessToken("tok-1", 600));
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void createOrder_sendsTheBodyWithBearerTokenAndCorrelationIdAndReturnsTheOrderId() {
        server.ordering(OrderingTestServer.json(200, CREATED));

        String orderId = client.session(CORRELATION).createOrder(ORDER);

        assertThat(orderId).isEqualTo("o-1");
        Received create = server.orderingRequests().getFirst();
        assertThat(create.method()).isEqualTo("POST");
        assertThat(create.path()).isEqualTo("/ordering/orders");
        assertThat(create.header("Authorization")).isEqualTo("Bearer tok-1");
        assertThat(create.header("correlationId")).isEqualTo(CORRELATION.toString());
        assertThat(create.header("Content-Type")).startsWith("application/json");
        assertThat(create.body()).contains("\"documentId\":\"" + DOCUMENT + "\"", "\"documentPremium\":34000.00");
    }

    @Test
    void tokenRequest_isClientCredentialsWithTheClientInTheForm() {
        server.ordering(OrderingTestServer.json(200, CREATED));

        client.session(CORRELATION).createOrder(ORDER);

        Received token = server.tokenRequests().getFirst();
        assertThat(token.method()).isEqualTo("POST");
        assertThat(token.body()).contains("grant_type=client_credentials", "client_id=contracts-stand",
                "client_secret=" + OrderingSettings.CLIENT_SECRET);
    }

    @Test
    void token_isReusedUntilItExpiresByTheServiceClock() {
        server.token(OrderingTestServer.accessToken("tok-2", 600));
        server.ordering(OrderingTestServer.json(200, CREATED));

        client.session(CORRELATION);
        clock.move(Duration.ofMinutes(1));
        client.session(CORRELATION);
        assertThat(server.tokenRequests()).hasSize(1);

        clock.move(Duration.ofMinutes(10));
        client.session(CORRELATION).createOrder(ORDER);

        assertThat(server.tokenRequests()).hasSize(2);
        assertThat(server.orderingRequests().getFirst().header("Authorization")).isEqualTo("Bearer tok-2");
    }

    @Test
    void refusedToken_isTemporaryAndDiscardedSoTheNextSessionAsksForANewOne() {
        server.token(OrderingTestServer.accessToken("tok-2", 600));
        server.ordering(OrderingTestServer.json(401, ""));
        server.ordering(OrderingTestServer.json(200, CREATED));
        OrderingSession first = client.session(CORRELATION);

        assertThatThrownBy(() -> first.createOrder(ORDER)).isInstanceOf(OrderingUnavailableException.class);
        String orderId = client.session(CORRELATION).createOrder(ORDER);

        assertThat(orderId).isEqualTo("o-1");
        assertThat(server.tokenRequests()).hasSize(2);
        assertThat(server.orderingRequests().get(1).header("Authorization")).isEqualTo("Bearer tok-2");
        assertThat(failures(OrderingOperation.CREATE_ORDER)).isEqualTo(1);
    }

    @Test
    void notRefusedFailure_keepsTheToken() {
        server.ordering(OrderingTestServer.json(503, ""));
        server.ordering(OrderingTestServer.json(200, CREATED));

        assertThatThrownBy(() -> client.session(CORRELATION).createOrder(ORDER))
                .isInstanceOf(OrderingUnavailableException.class);
        client.session(CORRELATION).createOrder(ORDER);

        assertThat(server.tokenRequests()).hasSize(1);
    }

    @Test
    void unreachableTokenServer_isATemporaryTokenFailureCountedOnce() {
        server.close();

        assertThatThrownBy(() -> client.session(CORRELATION))
                .isInstanceOfSatisfying(OrderingUnavailableException.class,
                        unavailable -> assertThat(unavailable.operation()).isEqualTo(OrderingOperation.TOKEN))
                .message().doesNotContain(OrderingSettings.CLIENT_SECRET);
        assertThat(failures(OrderingOperation.TOKEN)).isEqualTo(1);
    }

    @Test
    void tokenServerRefusal_isATemporaryTokenFailure() throws IOException {
        try (OrderingTestServer refusing = new OrderingTestServer()) {
            refusing.token(OrderingTestServer.json(401, "{\"error\": \"invalid_client\"}"));
            OrderingHttpClient refused = OrderingHttpClients.create(OrderingSettings.at(refusing.orderingUrl(),
                    refusing.tokenUri()), JsonMapper.builder().build(), new OrderingMetrics(registry), clock);

            assertThatThrownBy(() -> refused.session(CORRELATION))
                    .isInstanceOfSatisfying(OrderingUnavailableException.class,
                            unavailable -> assertThat(unavailable.operation()).isEqualTo(OrderingOperation.TOKEN));
        }
        assertThat(failures(OrderingOperation.TOKEN)).isEqualTo(1);
    }

    @Test
    void silentOrdering_timesOutAsTemporaryFailure() {
        server.ordering(server.silence());

        assertThatThrownBy(() -> client.session(CORRELATION).createOrder(ORDER))
                .isInstanceOf(OrderingUnavailableException.class)
                .hasMessageContaining("нет ответа");
        assertThat(failures(OrderingOperation.CREATE_ORDER)).isEqualTo(1);
    }

    @Test
    void droppedConnection_isTemporaryFailure() {
        server.ordering(OrderingTestServer.dropped());

        assertThatThrownBy(() -> client.session(CORRELATION).createOrder(ORDER))
                .isInstanceOf(OrderingUnavailableException.class);
    }

    @Test
    void successWithoutOrderId_isTemporaryAndCounted() {
        server.ordering(OrderingTestServer.json(200, "{\"status\": \"success\"}"));

        assertThatThrownBy(() -> client.session(CORRELATION).createOrder(ORDER))
                .isInstanceOf(OrderingUnavailableException.class);
        assertThat(failures(OrderingOperation.CREATE_ORDER)).isEqualTo(1);
    }

    @Test
    void rejection_isCountedAndCarriesTheCode() {
        server.ordering(OrderingTestServer.json(400,
                "{\"status\": \"error\", \"errors\": [{\"code\": \"CreateOrderValidatorService.valid\"}]}"));

        assertThatThrownBy(() -> client.session(CORRELATION).createOrder(ORDER))
                .isInstanceOfSatisfying(OrderingRejectedException.class,
                        rejected -> assertThat(rejected.code()).isEqualTo("CreateOrderValidatorService.valid"));
        assertThat(failures(OrderingOperation.CREATE_ORDER)).isEqualTo(1);
    }

    @Test
    void findOrder_asksTheOrdersOfTheClientForOurDocument() {
        server.ordering(OrderingTestServer.json(200, "{\"status\": \"success\", \"data\": {\"orderId\": \"o-1\", "
                + "\"orderDetails\": [{\"product\": \"rvl\", \"documentId\": \"" + DOCUMENT + "\"}]}}"));

        assertThat(client.session(CORRELATION).findOrder(CLIENT, DOCUMENT)).contains("o-1");
        Received find = server.orderingRequests().getFirst();
        assertThat(find.method()).isEqualTo("POST");
        assertThat(find.path()).isEqualTo("/ordering/orders/" + CLIENT);
        assertThat(find.body()).isEqualTo(
                "{\"orderDetails\":{\"product\":\"rvl\",\"documentId\":\"" + DOCUMENT + "\"}}");
    }

    @Test
    void orderStatus_isReadFromTheOrderDetails() {
        server.ordering(OrderingTestServer.json(200, "{\"status\": \"success\", \"data\": {\"status\": \"CANCELED\"}}"));

        assertThat(client.session(CORRELATION).orderStatus("o-1")).isEqualTo("CANCELED");
        assertThat(server.orderingRequests().getFirst().method()).isEqualTo("GET");
        assertThat(server.orderingRequests().getFirst().path()).isEqualTo("/ordering/orders/o-1");
    }

    @Test
    void cancelOrder_postsTheChannelAndReason() {
        server.ordering(OrderingTestServer.json(200, "{\"status\": \"success\"}"));

        client.session(CORRELATION).cancelOrder("o-1");

        Received cancel = server.orderingRequests().getFirst();
        assertThat(cancel.path()).isEqualTo("/ordering/orders/o-1/cancel");
        assertThat(cancel.body()).isEqualTo("{\"channel\":\"rvl-lk\",\"cancelReason\":\"notActual\"}");
        assertThat(cancel.header("correlationId")).isEqualTo(CORRELATION.toString());
    }

    @Test
    void cancelRejection_isCountedForCancellation() {
        server.ordering(OrderingTestServer.json(200, "{\"status\": \"error\", \"errors\": [{\"code\": \"C\"}]}"));

        assertThatThrownBy(() -> client.session(CORRELATION).cancelOrder("o-1"))
                .isInstanceOf(OrderingRejectedException.class);
        assertThat(failures(OrderingOperation.CANCEL_ORDER)).isEqualTo(1);
    }

    @Test
    void unreadableBody_isTemporary() {
        server.ordering(OrderingTestServer.json(200, "не JSON"));

        assertThatThrownBy(() -> client.session(CORRELATION).orderStatus("o-1"))
                .isInstanceOf(OrderingUnavailableException.class);
        assertThat(failures(OrderingOperation.ORDER_STATE)).isEqualTo(1);
    }

    private double failures(OrderingOperation operation) {
        return registry.get("contracts.ordering.failures").tag("operation", operation.tag()).counter().count();
    }
}
