package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.Before;
import io.cucumber.java.ru.Тогда;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.boot.test.web.server.LocalManagementPort;

public class PrometheusSteps {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final URI scrape;
    private String beforeScenario = "";

    public PrometheusSteps(@LocalManagementPort int managementPort) {
        this.scrape = URI.create("http://localhost:" + managementPort + "/actuator/prometheus");
    }

    @Before(order = 30)
    public void rememberCounters() throws IOException, InterruptedException {
        beforeScenario = scrape();
    }

    @Тогда("счётчик Prometheus {string} с меткой event_type {string} вырос за сценарий на {int}")
    public void counterGrewByEventType(String name, String eventType, int growth) throws IOException, InterruptedException {
        counterGrew(name, "event_type", eventType, growth);
    }

    @Тогда("счётчик Prometheus {string} с меткой reason {string} вырос за сценарий на {int}")
    public void counterGrewByReason(String name, String reason, int growth) throws IOException, InterruptedException {
        counterGrew(name, "reason", reason, growth);
    }

    private void counterGrew(String name, String label, String value, int growth) throws IOException, InterruptedException {
        double now = valueOf(scrape(), name, label, value);

        assertThat(now - valueOf(beforeScenario, name, label, value))
                .as("прирост %s{%s=\"%s\"} за сценарий", name, label, value)
                .isEqualTo(growth);
    }

    private String scrape() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(scrape).timeout(TIMEOUT).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("ответ /actuator/prometheus").isEqualTo(200);
        return response.body();
    }

    private static double valueOf(String exposition, String name, String label, String value) {
        Pattern series = Pattern.compile("^" + Pattern.quote(name) + "\\{[^}]*" + label + "=\"" + Pattern.quote(value)
                + "\"[^}]*} (\\S+)$", Pattern.MULTILINE);
        return series.matcher(exposition).results()
                .mapToDouble(match -> Double.parseDouble(match.group(1)))
                .sum();
    }
}
