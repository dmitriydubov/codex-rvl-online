package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import ru.projectx.datareference.support.IntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "projectx.api.max-concurrent-requests=" + ConcurrentRequestsLimitTest.LIMIT)
class ConcurrentRequestsLimitTest {

    static final int LIMIT = 3;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Duration IMMEDIATELY = Duration.ofSeconds(1);
    private static final String UNKNOWN_POLICY = "/internal/v1/policies/f662d1c4-5b26-4248-8ba1-47954abd301b";
    private static final String REQUEST_ID = "limit-test-1";
    private static final Pattern REJECTED = Pattern.compile("^rest_read_rejected_total (\\S+)$", Pattern.MULTILINE);
    private static final Pattern IN_FLIGHT = Pattern.compile("^rest_read_in_flight (\\S+)$", Pattern.MULTILINE);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final JsonMapper json = JsonMapper.shared();
    private final ExecutorService requesters = Executors.newVirtualThreadPerTaskExecutor();

    @LocalServerPort
    private int apiPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private HoldingController holding;

    @AfterEach
    void releaseHeldRequests() {
        holding.release();
        requesters.close();
    }

    @Test
    void requestBeyondLimit_gets503ImmediatelyInUnifiedFormat_whileProbesAndManagementAnswer() throws Exception {
        double rejectedBefore = metric(REJECTED);
        List<Future<HttpResponse<String>>> held = holdTheLimit();

        long started = System.nanoTime();
        HttpResponse<String> rejected = get(apiPort, UNKNOWN_POLICY, REQUEST_ID);
        Duration answeredIn = Duration.ofNanos(System.nanoTime() - started);

        assertThat(rejected.statusCode()).isEqualTo(503);
        assertThat(answeredIn).isLessThan(IMMEDIATELY);
        assertThat(rejected.headers().firstValue("X-Request-Id")).contains(REQUEST_ID);
        assertThat(rejected.headers().firstValue("Retry-After")).isEmpty();
        JsonNode error = json.readTree(rejected.body()).path("error");
        assertThat(error.path("code").asString()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(error.path("message").asString()).isEqualTo("сервис перегружен, повторите запрос позже");
        assertThat(error.path("details").isEmpty()).isTrue();
        assertThat(error.path("request_id").asString()).isEqualTo(REQUEST_ID);
        assertThat(get(apiPort, "/livez").statusCode()).isEqualTo(200);
        assertThat(get(apiPort, "/readyz").statusCode()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/health").statusCode()).isEqualTo(200);
        assertThat(metric(IN_FLIGHT)).isEqualTo(LIMIT);
        assertThat(metric(REJECTED)).isEqualTo(rejectedBefore + 1);

        holding.release();

        assertThat(statuses(held)).containsOnly(200);
        assertThat(get(apiPort, UNKNOWN_POLICY).statusCode()).isEqualTo(404);
        assertThat(metric(IN_FLIGHT)).isZero();
    }

    @Test
    void placesAreFreed_whenHeldRequestsEndWithInternalError() throws Exception {
        List<Future<HttpResponse<String>>> held = holdTheLimit();
        assertThat(get(apiPort, UNKNOWN_POLICY).statusCode()).isEqualTo(503);

        holding.releaseWithFailure();

        assertThat(statuses(held)).containsOnly(500);
        assertThat(get(apiPort, UNKNOWN_POLICY).statusCode()).isEqualTo(404);
    }

    private List<Future<HttpResponse<String>>> holdTheLimit() {
        holding.hold();
        List<Future<HttpResponse<String>>> held = IntStream.range(0, LIMIT)
                .mapToObj(request -> requesters.submit(() -> get(apiPort, HoldingController.HELD)))
                .toList();
        await().atMost(TIMEOUT).until(() -> holding.arrived() == LIMIT);
        return held;
    }

    private static List<Integer> statuses(List<Future<HttpResponse<String>>> responses)
            throws InterruptedException, ExecutionException, TimeoutException {

        List<Integer> statuses = new java.util.ArrayList<>();
        for (Future<HttpResponse<String>> response : responses) {
            statuses.add(response.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS).statusCode());
        }
        return statuses;
    }

    private double metric(Pattern series) throws IOException, InterruptedException {
        Matcher match = series.matcher(get(managementPort, "/actuator/prometheus").body());
        assertThat(match.find()).as("ряд %s в /actuator/prometheus", series).isTrue();
        return Double.parseDouble(match.group(1));
    }

    private HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
        return http.send(request(port, path).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(int port, String path, String requestId) throws IOException, InterruptedException {
        return http.send(request(port, path).header("X-Request-Id", requestId).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest.Builder request(int port, String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(TIMEOUT).GET();
    }
}
