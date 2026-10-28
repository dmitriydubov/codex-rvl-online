package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.cucumber.java.ParameterType;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import java.time.Duration;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

public class RequestSteps {

    private static final int OK = 200;
    private static final Duration UNTIL_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);

    private final ContractsService contracts;

    public RequestSteps(ContractsService contracts) {
        this.contracts = contracts;
    }

    @ParameterType(name = "метод", value = "GET|HEAD|OPTIONS|POST|PUT|PATCH|DELETE")
    public HttpMethod httpMethod(String name) {
        return HttpMethod.valueOf(name);
    }

    @Когда("сервис Договоров/Заявок запрашивает {string}")
    public void requests(String path) {
        contracts.request(path);
    }

    @Когда("мониторинг запрашивает {string}")
    public void monitoringRequests(String path) {
        contracts.monitor(path);
    }

    @Когда("Kubernetes запрашивает {string}")
    public void kubernetesProbes(String path) {
        contracts.request(path);
    }

    @Когда("мониторинг запрашивает {string}, пока поле {string} ответа не станет равно {string}")
    public void monitoringRequestsUntil(String path, String field, String expected) {
        await().atMost(UNTIL_TIMEOUT).pollInterval(POLL_INTERVAL)
                .until(() -> contracts.monitor(path).field(field),
                        value -> value.isString() && expected.equals(value.asString()));
    }

    @Когда("сервис Договоров/Заявок запрашивает {string} с X-Request-Id {string}")
    public void requestsWithRequestId(String path, String requestId) {
        contracts.request(path, requestId);
    }

    @Когда("сервис Договоров/Заявок отправляет {метод} на {string}")
    public void sends(HttpMethod method, String path) {
        contracts.send(method, path);
    }

    @Когда("сервис Договоров/Заявок запрашивает {string} с Accept {string}")
    public void requestsAccepting(String path, String accept) {
        contracts.requestAccepting(path, accept);
    }

    @Дано("сервис Договоров/Заявок получил id из ответа на {string}")
    public void obtainedId(String path) {
        obtainIdFrom("id", path);
    }

    @Дано("сервис Договоров/Заявок получил id из поля {string} ответа на {string}")
    public void obtainedIdFromField(String field, String path) {
        obtainIdFrom(field, path);
    }

    private void obtainIdFrom(String field, String path) {
        ServiceResponse response = contracts.request(path);
        assertThat(response.status()).as("статус ответа на %s: %s", path, response.body()).isEqualTo(OK);

        JsonNode id = response.field(field);
        assertThat(id.isString()).as("поле %s ответа на %s — строка: %s", field, path, response.body()).isTrue();
        assertThat(id.asString()).as("поле %s ответа на %s", field, path).isNotBlank();
        contracts.obtainId(id.asString());
    }
}
