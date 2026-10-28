package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.cucumber.java.Before;
import io.cucumber.java.ru.Тогда;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.test.web.server.LocalManagementPort;

public class PrometheusSteps {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration GAUGE_TOLERANCE = Duration.ofMinutes(1);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final URI scrape;
    private final List<String> scenarioScrapes = new ArrayList<>();
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

    @Тогда("счётчик Prometheus {string} вырос за сценарий на {int}")
    public void counterWithoutLabelsGrew(String name, int growth) throws IOException, InterruptedException {
        String series = Pattern.quote(name) + " ";
        double now = sum(seriesValues(scrape(), series));

        assertThat(now - sum(seriesValues(beforeScenario, series)))
                .as("прирост %s за сценарий", name)
                .isEqualTo(growth);
    }

    private static double sum(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).sum();
    }

    @Тогда("показатель Prometheus {string} равен {int}")
    public void gaugeEquals(String name, int expected) throws IOException, InterruptedException {
        assertThat(seriesValues(scrape(), Pattern.quote(name) + " "))
                .as("ряд %s без меток", name)
                .containsExactly((double) expected);
    }

    @Тогда("показатель Prometheus {string} с метками stream {string} и status {string} равен {int}")
    public void gaugeWithStreamAndStatusEquals(String name, String stream, String status, int expected)
            throws IOException, InterruptedException {

        assertThat(seriesValues(scrape(), Pattern.quote(name) + "\\{(?=[^}]*stream=\"" + Pattern.quote(stream)
                + "\")(?=[^}]*status=\"" + Pattern.quote(status) + "\")[^}]*} "))
                .as("ряд %s{stream=\"%s\",status=\"%s\"}", name, stream, status)
                .containsExactly((double) expected);
    }

    @Тогда("показатель Prometheus {string} с меткой pool {string} равен {int}")
    public void gaugeWithPoolEquals(String name, String pool, int expected) throws IOException, InterruptedException {
        assertThat(seriesValues(scrape(), Pattern.quote(name) + "\\{[^}]*pool=\"" + Pattern.quote(pool) + "\"[^}]*} "))
                .as("ряд %s{pool=\"%s\"}", name, pool)
                .containsExactly((double) expected);
    }

    @Тогда("показатель Prometheus {string} с меткой stream {string} — {int} ч с точностью до минуты")
    public void gaugeWithStreamInHours(String name, String stream, int hours) throws IOException, InterruptedException {
        assertThat(valueOf(scrape(), name, stream))
                .as("ряд %s{stream=\"%s\"}, секунды", name, stream)
                .isCloseTo((double) Duration.ofHours(hours).toSeconds(), within((double) GAUGE_TOLERANCE.toSeconds()));
    }

    @Тогда("ряда Prometheus {string} нет")
    public void noSeries(String name) throws IOException, InterruptedException {
        boolean shownEarlier = scenarioScrapes.stream().anyMatch(exposition -> hasSeries(exposition, name));
        String now = scrape();

        assertThat(shownEarlier)
                .as("ряды %s в ответах /actuator/prometheus раньше в сценарии", name)
                .isTrue();
        assertThat(hasSeries(now, name))
                .as("ряды %s в ответе /actuator/prometheus", name)
                .isFalse();
    }

    private static boolean hasSeries(String exposition, String name) {
        return Pattern.compile("^" + Pattern.quote(name) + "[{ ]", Pattern.MULTILINE).matcher(exposition).find();
    }

    private static List<Double> seriesValues(String exposition, String series) {
        return Pattern.compile("^" + series + "(\\S+)$", Pattern.MULTILINE).matcher(exposition).results()
                .map(match -> Double.parseDouble(match.group(1)))
                .toList();
    }

    private String scrape() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(scrape).timeout(TIMEOUT).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("ответ /actuator/prometheus").isEqualTo(200);
        scenarioScrapes.add(response.body());
        return response.body();
    }

    private static double valueOf(String exposition, String name, String stream) {
        Pattern series = Pattern.compile("^" + Pattern.quote(name) + "\\{[^}]*stream=\"" + Pattern.quote(stream)
                + "\"[^}]*} (\\S+)$", Pattern.MULTILINE);
        return series.matcher(exposition).results()
                .mapToDouble(match -> Double.parseDouble(match.group(1)))
                .sum();
    }
}
