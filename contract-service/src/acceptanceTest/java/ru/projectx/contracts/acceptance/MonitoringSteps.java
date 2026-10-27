package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Когда;
import io.cucumber.java.ru.Тогда;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.web.server.LocalManagementPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class MonitoringSteps {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration PATIENCE = Duration.ofSeconds(45);
    private static final Pattern SEGMENT = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)(?:\\[(\\d+)])?");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final JsonMapper json;
    private final String managementBase;
    private @Nullable HttpResponse<String> lastResponse;

    public MonitoringSteps(@LocalManagementPort int managementPort, JsonMapper json) {
        this.json = json;
        this.managementBase = "http://localhost:" + managementPort;
    }

    @Когда("мониторинг запрашивает {string}")
    public void monitoringRequests(String path) throws IOException, InterruptedException {
        lastResponse = get(path);
    }

    @Когда("мониторинг запрашивает {string}, пока поле {string} ответа не станет равно {string}")
    public void monitoringRequestsUntil(String path, String field, String expected) {
        Awaitility.await().atMost(PATIENCE).untilAsserted(() -> {
            lastResponse = get(path);
            assertThat(field(field)).as("поле %s ответа %s", field, path).hasValue(expected);
        });
    }

    @Тогда("Договоры ответили мониторингу {int}")
    public void answeredWithStatus(int status) {
        assertThat(response().statusCode()).as("HTTP-статус ответа мониторингу").isEqualTo(status);
    }

    @Тогда("поле {string} ответа равно {string}")
    public void fieldEquals(String field, String expected) {
        assertThat(field(field)).as("поле %s ответа", field).hasValue(expected);
    }

    @Тогда("поля {string} в ответе нет")
    public void fieldAbsent(String field) {
        assertThat(field(field)).as("поле %s ответа", field).isEmpty();
    }

    private Optional<String> field(String path) {
        JsonNode node = json.readTree(response().body());
        Matcher segments = SEGMENT.matcher(path);
        while (segments.find()) {
            node = node.path(segments.group(1));
            if (segments.group(2) != null) {
                node = node.path(Integer.parseInt(segments.group(2)));
            }
        }
        return node.isMissingNode() || node.isNull() ? Optional.empty() : Optional.of(node.asString());
    }

    private HttpResponse<String> response() {
        return Optional.ofNullable(lastResponse)
                .orElseThrow(() -> new AssertionError("мониторинг в сценарии ещё ничего не запрашивал"));
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(managementBase + path)).timeout(TIMEOUT).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
