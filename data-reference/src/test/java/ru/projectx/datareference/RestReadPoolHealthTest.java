package ru.projectx.datareference;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import ru.projectx.datareference.support.ExhaustedPool;
import ru.projectx.datareference.support.IntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Всплеск REST-чтений при живой базе — не повод выводить экземпляр из работы, поэтому здоровье и готовность
 * остаются в строю. Нехватку соединений видно по метрикам с меткой {@code pool}, и оповещение о приёме и разборе
 * смотрит только на {@code pool="application"}.
 */
@IntegrationTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RestReadPoolHealthTest {

    private static final int OK = 200;
    private static final int SERVICE_UNAVAILABLE = 503;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String UNKNOWN_POLICY = "/internal/v1/policies/f662d1c4-5b26-4248-8ba1-47954abd301b";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort
    private int apiPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private DataSource dataSource;

    @Test
    void restReadPoolExhausted_healthStaysUp() throws SQLException, IOException, InterruptedException {
        try (ExhaustedPool ignored = ExhaustedPool.ofRestReads(dataSource)) {
            HttpResponse<String> health = get(managementPort, "/actuator/health");

            assertThat(health.statusCode()).isEqualTo(OK);
            JsonNode body = json.readTree(health.body());
            assertThat(body.path("status").asString()).isEqualTo("UP");
            assertThat(body.path("components").path("db").path("status").asString()).isEqualTo("UP");
        }
    }

    @Test
    void restReadPoolExhausted_readinessStaysUp() throws SQLException, IOException, InterruptedException {
        try (ExhaustedPool ignored = ExhaustedPool.ofRestReads(dataSource)) {
            assertThat(get(managementPort, "/actuator/health/readiness").statusCode()).isEqualTo(OK);
        }
    }

    /** Отказ занятого пула REST-чтений считает счётчик этого пула, а не пула приложения. */
    @Test
    void restReadPoolTimeout_countedUnderItsOwnPoolLabel() throws SQLException, IOException, InterruptedException {
        try (ExhaustedPool ignored = ExhaustedPool.ofRestReads(dataSource)) {
            assertThat(get(apiPort, UNKNOWN_POLICY).statusCode()).isEqualTo(SERVICE_UNAVAILABLE);
        }

        String metrics = get(managementPort, "/actuator/prometheus").body();

        assertThat(counter(metrics, "rest-read")).isGreaterThanOrEqualTo(1.0);
        assertThat(counter(metrics, "application")).isZero();
    }

    /** Долю отказов REST считают по счётчику запросов сервера с меткой статуса и шаблоном пути ручки. */
    @Test
    void restReadPoolTimeout_countedAsServiceUnavailableRequest() throws SQLException, IOException, InterruptedException {
        try (ExhaustedPool ignored = ExhaustedPool.ofRestReads(dataSource)) {
            assertThat(get(apiPort, UNKNOWN_POLICY).statusCode()).isEqualTo(SERVICE_UNAVAILABLE);
        }

        String metrics = get(managementPort, "/actuator/prometheus").body();

        assertThat(metrics.lines())
                .anySatisfy(line -> assertThat(line)
                        .startsWith("http_server_requests_seconds_count{")
                        .contains("status=\"503\"", "uri=\"/internal/v1/policies/{id}\""));
    }

    private static double counter(String metrics, String pool) {
        String series = "hikaricp_connections_timeout_total{pool=\"" + pool + "\"} ";
        return metrics.lines()
                .filter(line -> line.startsWith(series))
                .map(line -> Double.parseDouble(line.substring(series.length())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("нет ряда " + series.strip() + " в выдаче /actuator/prometheus"));
    }

    private HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(TIMEOUT).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
