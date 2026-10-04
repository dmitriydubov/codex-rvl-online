package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ParameterType;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/**
 * Запросы сервисов Договоров и Заявок. {@code {id}} в пути означает id, полученный на шаге «Дано»: так
 * потребитель и работает, он хранит наш id и адресует им запись.
 *
 * <p>Оба потребителя ходят одним HTTP-клиентом и одними шагами. Запись «сервис Договоров/Заявок» в
 * выражении шага означает любой из двух. Для Дата-референса потребители неразличимы, потому что
 * аутентификации между сервисами нет, и отличаются они только тем, какую ручку читают. Имя потребителя
 * в сценарии говорит читателю, чья это ручка.
 *
 * <p>Метод запроса шаг принимает только из стандартных имён. Нестандартное имя сервис тоже отклонил бы
 * с 405, поэтому сценарий с опечаткой в методе прошёл бы, проверяя не тот запрос, что в нём написан.
 */
public class RequestSteps {

    private static final int OK = 200;

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
