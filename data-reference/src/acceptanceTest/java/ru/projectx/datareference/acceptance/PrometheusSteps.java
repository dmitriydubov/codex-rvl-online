package ru.projectx.datareference.acceptance;

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

/**
 * Шаги о счётчиках в выдаче {@code /actuator/prometheus} на порту мониторинга: так их видит Prometheus контура.
 *
 * <p>Счётчики общие на весь прогон приёмки, поэтому шаг сравнивает значение ряда с тем, что было перед сценарием, а
 * не с нулём. Выдача перед сценарием снимается тем же запросом, что и в шаге.
 */
public class PrometheusSteps {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final URI scrape;
    private String beforeScenario = "";

    public PrometheusSteps(@LocalManagementPort int managementPort) {
        this.scrape = URI.create("http://localhost:" + managementPort + "/actuator/prometheus");
    }

    @Before
    public void rememberCounters() throws IOException, InterruptedException {
        beforeScenario = scrape();
    }

    @Тогда("счётчик Prometheus {string} с меткой stream {string} вырос за сценарий на {int}")
    public void counterGrew(String name, String stream, int growth) throws IOException, InterruptedException {
        double now = valueOf(scrape(), name, stream);

        assertThat(now - valueOf(beforeScenario, name, stream))
                .as("прирост %s{stream=\"%s\"} за сценарий", name, stream)
                .isEqualTo(growth);
    }

    private String scrape() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(scrape).timeout(TIMEOUT).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("ответ /actuator/prometheus").isEqualTo(200);
        return response.body();
    }

    /** Значение ряда с меткой {@code stream}; ряда нет — ноль. Другие метки ряда шаг не сравнивает. */
    private static double valueOf(String exposition, String name, String stream) {
        Pattern series = Pattern.compile("^" + Pattern.quote(name) + "\\{[^}]*stream=\"" + Pattern.quote(stream)
                + "\"[^}]*} (\\S+)$", Pattern.MULTILINE);
        return series.matcher(exposition).results()
                .mapToDouble(match -> Double.parseDouble(match.group(1)))
                .sum();
    }
}
