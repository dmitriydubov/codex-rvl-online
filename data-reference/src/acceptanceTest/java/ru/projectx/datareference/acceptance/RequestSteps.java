package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import tools.jackson.databind.JsonNode;

/**
 * Запросы сервисов Договоров и Заявок, а также мониторинга к ручке здоровья. {@code {id}} в пути — id,
 * полученный на шаге «Дано»: так потребитель и работает — хранит наш id и адресует им запись.
 *
 * <p>Оба потребителя ходят одним HTTP-клиентом и одними шагами («сервис Договоров/Заявок» —
 * альтернатива Cucumber Expressions): для Дата-референса они неразличимы — межсервисной
 * аутентификации нет (Д5), и отличаются они только тем, какую ручку читают. Имя потребителя
 * в сценарии говорит читателю, чья это ручка.
 */
public class RequestSteps {

    private static final int OK = 200;

    private final ContractsService contracts;

    public RequestSteps(ContractsService contracts) {
        this.contracts = contracts;
    }

    @Когда("сервис Договоров/Заявок запрашивает {string}")
    public void requests(String path) {
        contracts.request(path);
    }

    /** Ручку здоровья читают Kubernetes и мониторинг — тем же HTTP, что и соседние сервисы. */
    @Когда("мониторинг запрашивает {string}")
    public void monitoringRequests(String path) {
        contracts.request(path);
    }

    @Когда("сервис Договоров/Заявок запрашивает {string} с X-Request-Id {string}")
    public void requestsWithRequestId(String path, String requestId) {
        contracts.request(path, requestId);
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
