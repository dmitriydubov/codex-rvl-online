package ru.projectx.datareference;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * Актуатор отвечает на своём порту, а не на порту API: сервисы, которым открыт REST, не видят ни состояния
 * компонентов, ни метрик. Пробы Kubernetes есть на обоих портах. На порту API — только короткие {@code /livez}
 * и {@code /readyz}: так проба проверяет тот же сервер, что принимает запросы соседей, и не проходит, когда
 * он не отвечает, а работает лишь порт актуатора.
 *
 * <p>Запросы — настоящим HTTP к запущенному серверу: в контексте без сервера порт актуатора не поднимается
 * вовсе, и такой тест ничего бы не проверил.
 */
@IntegrationTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = ManagementPortTest.INFO_PROPERTY_FROM_ENVIRONMENT)
class ManagementPortTest {

    /**
     * Свойство {@code info.*}, как если бы его задало окружение развёртывания: info не показывает и его, потому что
     * свойства окружения в info не публикуются.
     */
    static final String INFO_PROPERTY_FROM_ENVIRONMENT = "info.contour=test-contour";
    private static final String MODULE_NAME = "data-reference";

    private static final int OK = 200;
    private static final int NOT_FOUND = 404;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort
    private int apiPort;

    @LocalManagementPort
    private int managementPort;

    @Test
    void managementPort_differsFromApiPort() {
        assertThat(managementPort).isPositive().isNotEqualTo(apiPort);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health/liveness", "/actuator/health/readiness"})
    void managementPort_answersKubernetesProbesUp(String path) throws IOException, InterruptedException {
        assertUp(get(managementPort, path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/livez", "/readyz"})
    void apiPort_answersKubernetesProbesUp(String path) throws IOException, InterruptedException {
        assertUp(get(apiPort, path));
    }

    /** Порт API видят соседи: короткие пробы отдают одно состояние, без компонентов и их деталей. */
    @ParameterizedTest
    @ValueSource(strings = {"/livez", "/readyz"})
    void apiPort_probesShowStatusOnly(String path) throws IOException, InterruptedException {
        JsonNode probe = json.readTree(get(apiPort, path).body());

        assertThat(probe.propertyNames()).containsExactly("status");
    }

    /** Состав health и его детали видит только тот, кому открыт порт актуатора. */
    @Test
    void managementPort_showsHealthComponents() throws IOException, InterruptedException {
        HttpResponse<String> health = get(managementPort, "/actuator/health");

        assertThat(health.statusCode()).isEqualTo(OK);
        JsonNode database = json.readTree(health.body()).path("components").path("db");
        assertThat(database.path("status").asString()).isEqualTo("UP");
        assertThat(database.path("details").path("database").asString()).isEqualTo("PostgreSQL");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/actuator", "/actuator/health", "/actuator/health/liveness", "/actuator/prometheus", "/actuator/info"})
    void apiPort_doesNotServeActuator(String path) throws IOException, InterruptedException {
        assertThat(get(apiPort, path).statusCode()).isEqualTo(NOT_FOUND);
    }

    /**
     * Сопровождение видит, какая версия установлена: та, что записана в сборке модуля. Версия берётся из того же
     * файла, что и у сборки, поэтому тест не правится при каждом выпуске.
     */
    @Test
    void managementPort_showsServiceVersion() throws IOException, InterruptedException {
        HttpResponse<String> info = get(managementPort, "/actuator/info");

        assertThat(info.statusCode()).isEqualTo(OK);
        assertThat(json.readTree(info.body()).path("build").path("version").asString())
                .isEqualTo(buildProperty("version"));
    }

    /** Сведения о сборке называют этот модуль: группу из его сборки и его имя. */
    @Test
    void managementPort_infoNamesThisModule() throws IOException, InterruptedException {
        JsonNode build = json.readTree(get(managementPort, "/actuator/info").body()).path("build");

        assertThat(build.path("group").asString()).isEqualTo(buildProperty("group"));
        assertThat(build.path("artifact").asString()).isEqualTo(MODULE_NAME);
        assertThat(build.path("name").asString()).isEqualTo(MODULE_NAME);
    }

    /**
     * В info — только сведения о сборке: ни переменных окружения и свойств (там адреса и пароли), даже заданных
     * с префиксом {@code info.}, ни сведений о Java, процессе и машине. Время сборки не публикуется: с ним каждая сборка давала бы другой jar.
     */
    @Test
    void managementPort_infoShowsBuildFactsOnly() throws IOException, InterruptedException {
        JsonNode info = json.readTree(get(managementPort, "/actuator/info").body());

        assertThat(info.propertyNames()).containsExactly("build");
        assertThat(info.path("build").propertyNames()).containsExactlyInAnyOrder("artifact", "name", "group", "version");
    }

    /** Наружу и на порту актуатора открыты только health, info и prometheus: открыть остальное — одна строка конфигурации. */
    @Test
    void managementPort_doesNotExposeOtherEndpoints() throws IOException, InterruptedException {
        assertThat(get(managementPort, "/actuator/env").statusCode()).isEqualTo(NOT_FOUND);
    }

    /**
     * Prometheus забирает метрики приёма и разбора с той же ручки, что и метрики самого Spring Boot. Ряды
     * приёма и разбора есть сразу после старта, ещё до первого сообщения: нулевой ряд для правил оповещения
     * однозначнее отсутствующего. Ряд переходов взят у потока видов обращения: сообщения этого потока тесты
     * не отклоняют. Метрика слушателей очередей Spring AMQP здесь не проверяется: в этом контексте
     * брокера нет.
     */
    @Test
    void managementPort_exposesIngestAndParsingMetrics() throws IOException, InterruptedException {
        HttpResponse<String> metrics = get(managementPort, "/actuator/prometheus");

        assertThat(metrics.statusCode()).isEqualTo(OK);
        assertThat(metrics.body())
                .contains("onec_ingest_deliveries_total{outcome=\"unidentified\",queue=\"1c-usk_to_rvl-online_policies\","
                        + "stream=\"POLICIES\"} 0.0")
                .contains("onec_ingest_pauses_total 0.0")
                .contains("onec_ingest_paused 0.0")
                .contains("onec_inbound_transitions_total{status=\"FAILED\",stream=\"APPEAL_TYPES\"} 0.0")
                .contains("onec_inbound_messages{status=\"NEW\",stream=\"POLICIES\"} 0.0")
                .contains("onec_inbound_pending_oldest_age_seconds{stream=\"PRICE_LISTS\"} 0.0")
                .contains("onec_price_transfers_stuck 0.0")
                .contains("onec_retention_overdue_payloads 0.0")
                .contains("onec_retention_overdue_quarantine_bodies 0.0")
                .contains("# TYPE onec_inbound_backlog_staleness_seconds gauge")
                .contains("# HELP onec_inbound_messages Сырые сообщения 1С по потоку и статусу")
                .contains("hikaricp_connections");
    }

    private static String buildProperty(String name) throws IOException {
        Properties build = new Properties();
        try (Reader file = Files.newBufferedReader(Path.of("gradle.properties"), StandardCharsets.UTF_8)) {
            build.load(file);
        }
        return build.getProperty(name);
    }

    private void assertUp(HttpResponse<String> probe) {
        assertThat(probe.statusCode()).as(probe.body()).isEqualTo(OK);
        assertThat(json.readTree(probe.body()).path("status").asString()).isEqualTo("UP");
    }

    private HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(TIMEOUT)
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
