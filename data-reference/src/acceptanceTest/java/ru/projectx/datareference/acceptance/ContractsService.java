package ru.projectx.datareference.acceptance;

import io.cucumber.spring.ScenarioScope;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Сервис Договоров в роли потребителя: настоящие HTTP-запросы к запущенному Дата-референсу
 * ({@code RANDOM_PORT}) и то, что потребитель помнит между шагами сценария, —
 * последний ответ и id, полученный на шаге «Дано».
 *
 * <p>Клиент — {@code RestTestClient.bindToServer(ClientHttpRequestFactory)} из spring-test: автонастройка Boot
 * для него ({@code spring-boot-resttestclient}) — отдельный модуль, которого нет
 * на classpath и в Nexus контура.
 *
 * <p>{@code {id}} в пути раскрывается как переменная URI-шаблона {@code RestTestClient},
 * а не заменой строки. Таймауты заданы явно: зависший запрос иначе держал бы весь прогон
 * приёмки до таймаута CI, не называя сценарий.
 */
@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
public class ContractsService {

    /** Переменная URI-шаблона: id, полученный на шаге «Дано». */
    private static final String OBTAINED_ID = "{id}";
    private static final String PATH_ROOT = "/";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestTestClient client;
    private @Nullable ServiceResponse lastResponse;
    private @Nullable String obtainedId;

    public ContractsService(@LocalServerPort int port) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(CONNECT_TIMEOUT);
        requests.setReadTimeout(READ_TIMEOUT);
        this.client = RestTestClient.bindToServer(requests).baseUrl("http://localhost:" + port).build();
    }

    public ServiceResponse request(String path) {
        return exchange(path, headers -> { });
    }

    public ServiceResponse request(String path, String requestId) {
        return exchange(path, headers -> headers.set(ServiceResponse.REQUEST_ID, requestId));
    }

    public ServiceResponse lastResponse() {
        return Optional.ofNullable(lastResponse)
                .orElseThrow(() -> new IllegalStateException("сценарий ещё ничего не запрашивал"));
    }

    public void obtainId(String id) {
        this.obtainedId = id;
    }

    public String obtainedId() {
        return Optional.ofNullable(obtainedId)
                .orElseThrow(() -> new IllegalStateException("сценарий не получил id на шаге «Дано»"));
    }

    private ServiceResponse exchange(String path, Consumer<HttpHeaders> headers) {
        requireAbsolutePath(path);
        lastResponse = ServiceResponse.of(
                client.get().uri(path, uriVariables(path)).headers(headers).exchange().returnResult());
        return lastResponse;
    }

    /**
     * Путь из сценария уходит потребителем как есть. Клиент сам дописал бы «/» между адресом сервера
     * и относительным путём, и сценарий с путём без ведущего «/» проверял бы не тот запрос, что в нём
     * написан. Цель HTTP-запроса к серверу всегда начинается с «/».
     */
    private static void requireAbsolutePath(String path) {
        if (!path.startsWith(PATH_ROOT)) {
            throw new IllegalArgumentException("путь запроса в сценарии должен начинаться с «/»: " + path);
        }
    }

    private Object[] uriVariables(String path) {
        return path.contains(OBTAINED_ID) ? new Object[] {obtainedId()} : new Object[0];
    }
}
