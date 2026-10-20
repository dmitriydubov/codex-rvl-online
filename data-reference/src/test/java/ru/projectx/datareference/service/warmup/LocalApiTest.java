package ru.projectx.datareference.service.warmup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class LocalApiTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final String PATH = "/internal/v1/lpu?contract_external_id=77ff17b5-e659-4720-b57f-e8a19c00fcea";

    private final Map<String, List<String>> received = new ConcurrentHashMap<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/internal/v1/lpu", exchange -> answer(exchange, 503));
        server.createContext("/internal/v1/programs", exchange -> answer(exchange, 200));
        server.createContext("/internal/v1/slow", this::answerAfterRelease);
        server.start();
    }

    @AfterEach
    void stopServer() {
        release.countDown();
        server.stop(0);
    }

    @Test
    void withoutApiServerPort_thereIsNothingToConnectTo() {
        assertThat(new LocalApi(new MockEnvironment()).connect(CONNECT_TIMEOUT)).isEmpty();
    }

    @Test
    void apiServerAddress_isLoopback() {
        MockEnvironment environment = new MockEnvironment().withProperty("local.server.port", "18080");
        try (LocalApiClient api = new LocalApi(environment).connect(CONNECT_TIMEOUT).orElseThrow()) {
            assertThat(api.address()).isEqualTo(URI.create("http://localhost:18080"));
        }
    }

    @Test
    void readsTheApiServerOnLocalhost_andReturnsTheStatus() throws Exception {
        try (LocalApiClient api = connect()) {
            assertThat(api.read(PATH, READ_TIMEOUT)).isEqualTo(503);
            assertThat(api.read("/internal/v1/programs/1", READ_TIMEOUT)).isEqualTo(200);
        }
        assertThat(received.get("uri")).containsExactly(PATH, "/internal/v1/programs/1");
    }

    @Test
    void asksForJsonUnderTheWarmupRequestId() throws Exception {
        try (LocalApiClient api = connect()) {
            api.read(PATH, READ_TIMEOUT);
        }
        assertThat(received.get("accept")).containsExactly("application/json");
        assertThat(received.get("requestId")).containsExactly("startup-warmup");
        assertThat(received.get("method")).containsExactly("GET");
    }

    @Test
    void answerSlowerThanTheTimeout_failsTheRead() {
        try (LocalApiClient api = connect()) {
            assertThatExceptionOfType(HttpTimeoutException.class)
                    .isThrownBy(() -> api.read("/internal/v1/slow", Duration.ofMillis(200)));
        }
    }

    @Test
    void closedClient_acceptsNoMoreReads() {
        LocalApiClient api = connect();
        api.close();

        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> api.read("/internal/v1/programs/1", READ_TIMEOUT));
        assertThat(received.get("uri")).isNull();
    }

    private LocalApiClient connect() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("local.server.port", String.valueOf(server.getAddress().getPort()));
        return new LocalApi(environment).connect(CONNECT_TIMEOUT).orElseThrow();
    }

    private void answer(HttpExchange exchange, int status) throws IOException {
        remember("uri", exchange.getRequestURI().toString());
        remember("accept", exchange.getRequestHeaders().getFirst("Accept"));
        remember("requestId", exchange.getRequestHeaders().getFirst("X-Request-Id"));
        remember("method", exchange.getRequestMethod());
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private void remember(String what, String value) {
        received.computeIfAbsent(what, key -> new CopyOnWriteArrayList<>()).add(value);
    }

    private void answerAfterRelease(HttpExchange exchange) throws IOException {
        try {
            release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        answer(exchange, 200);
    }
}
