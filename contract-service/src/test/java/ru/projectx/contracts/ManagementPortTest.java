package ru.projectx.contracts;

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
import ru.projectx.contracts.support.IntegrationTest;

/**
 * Актуатор отвечает на своём порту, чтобы соседи с доступом к API не видели состояния компонентов и метрик.
 * На порту API только короткие {@code /livez} и {@code /readyz}: проба проверяет тот сервер, что принимает запросы.
 * Запросы идут настоящим HTTP: без запущенного сервера порт актуатора не поднимается вовсе.
 */
@IntegrationTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = ManagementPortTest.INFO_PROPERTY_FROM_ENVIRONMENT)
class ManagementPortTest {

    /** Свойство {@code info.*}, как от окружения развёртывания: свойства окружения в info не публикуются. */
    static final String INFO_PROPERTY_FROM_ENVIRONMENT = "info.contour=test-contour";
    private static final String MODULE_NAME = "contract-service";

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

    /** Версия берётся из того же файла, что и у сборки, поэтому тест не правится при каждом выпуске. */
    @Test
    void managementPort_showsServiceVersion() throws IOException, InterruptedException {
        HttpResponse<String> info = get(managementPort, "/actuator/info");

        assertThat(info.statusCode()).isEqualTo(OK);
        assertThat(json.readTree(info.body()).path("build").path("version").asString())
                .isEqualTo(buildProperty("version"));
    }

    @Test
    void managementPort_infoNamesThisModule() throws IOException, InterruptedException {
        JsonNode build = json.readTree(get(managementPort, "/actuator/info").body()).path("build");

        assertThat(build.path("group").asString()).isEqualTo(buildProperty("group"));
        assertThat(build.path("artifact").asString()).isEqualTo(MODULE_NAME);
        assertThat(build.path("name").asString()).isEqualTo(MODULE_NAME);
    }

    /**
     * В info нет ни свойств окружения (там адреса и пароли), ни сведений о Java, процессе и машине.
     * Время сборки тоже не публикуется: с ним каждая сборка давала бы другой jar.
     */
    @Test
    void managementPort_infoShowsBuildFactsOnly() throws IOException, InterruptedException {
        JsonNode info = json.readTree(get(managementPort, "/actuator/info").body());

        assertThat(info.propertyNames()).containsExactly("build");
        assertThat(info.path("build").propertyNames()).containsExactlyInAnyOrder("artifact", "name", "group", "version");
    }

    /** Открыты только health, info и prometheus: открыть остальное — одна строка конфигурации. */
    @Test
    void managementPort_doesNotExposeOtherEndpoints() throws IOException, InterruptedException {
        assertThat(get(managementPort, "/actuator/env").statusCode()).isEqualTo(NOT_FOUND);
    }

    /** Метрики пула видны под его именем: по метке pool строится оповещение о нехватке соединений. */
    @Test
    void managementPort_exposesPoolMetrics() throws IOException, InterruptedException {
        HttpResponse<String> metrics = get(managementPort, "/actuator/prometheus");

        assertThat(metrics.statusCode()).isEqualTo(OK);
        assertThat(metrics.body()).contains("hikaricp_connections_max{pool=\"application\"}");
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
