package ru.projectx.datareference.acceptance;

import io.cucumber.spring.ScenarioScope;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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
 * <p>Запросы уходят через {@link HttpClient} из JDK по HTTP/1.1. Он выбран потому, что сценарии отказа
 * по методу отправляют PATCH, а клиент на {@code HttpURLConnection} этот метод не умеет.
 *
 * <p>{@code {id}} в пути раскрывается как переменная URI-шаблона {@code RestTestClient},
 * а не заменой строки. Таймауты заданы явно: зависший запрос иначе держал бы весь прогон
 * приёмки до таймаута CI, не называя сценарий.
 *
 * <p>Мониторинг ходит тем же клиентом, но на порт актуатора ({@code local.management.port}): на порту API
 * актуатора нет. Его ответ — тоже последний ответ, и проверяют его те же шаги.
 */
@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
public class ContractsService {

    /** Переменная URI-шаблона: id, полученный на шаге «Дано». */
    private static final String OBTAINED_ID = "{id}";
    private static final String PATH_ROOT = "/";
    private static final String QUERY_START = "?";
    private static final String PARAMETER_SEPARATOR = "&";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestTestClient client;
    private final RestTestClient monitoringClient;
    private @Nullable ServiceResponse lastResponse;
    private @Nullable String obtainedId;

    public ContractsService(@LocalServerPort int port, @LocalManagementPort int managementPort) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(READ_TIMEOUT);
        this.client = clientOn(requests, port);
        this.monitoringClient = clientOn(requests, managementPort);
    }

    public ServiceResponse request(String path) {
        return exchange(HttpMethod.GET, path, headers -> { });
    }

    /** Запрос мониторинга или Kubernetes к актуатору — на его порт. */
    public ServiceResponse monitor(String path) {
        requireAbsolutePath(path);
        lastResponse = ServiceResponse.of(monitoringClient.get().uri(path).exchange().returnResult());
        return lastResponse;
    }

    public ServiceResponse request(String path, String requestId) {
        return exchange(HttpMethod.GET, path, headers -> headers.set(ServiceResponse.REQUEST_ID, requestId));
    }

    /**
     * Запрос с ещё одним параметром, значение которого уходит переменной URI-шаблона. Клиент кодирует такое
     * значение целиком: пробел становится {@code %20}, запятая — {@code %2C}, кириллица — байтами UTF-8, и
     * сервер получает ровно ту строку, которую собрал шаг.
     */
    public ServiceResponse requestWithParameter(String path, String parameter, String value) {
        String separator = path.contains(QUERY_START) ? PARAMETER_SEPARATOR : QUERY_START;
        String template = path + separator + parameter + "={" + parameter + "}";
        return exchange(HttpMethod.GET, template, headers -> { }, value);
    }

    public ServiceResponse send(HttpMethod method, String path) {
        return exchange(method, path, headers -> { });
    }

    public ServiceResponse requestAccepting(String path, String accept) {
        return exchange(HttpMethod.GET, path, headers -> headers.set(HttpHeaders.ACCEPT, accept));
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

    private ServiceResponse exchange(
            HttpMethod method, String path, Consumer<HttpHeaders> headers, Object... parameterValues) {

        requireAbsolutePath(path);
        Object[] variables = Stream.concat(Arrays.stream(uriVariables(path)), Arrays.stream(parameterValues))
                .toArray();
        lastResponse = ServiceResponse.of(
                client.method(method).uri(path, variables).headers(headers).exchange().returnResult());
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

    private static RestTestClient clientOn(JdkClientHttpRequestFactory requests, int port) {
        return RestTestClient.bindToServer(requests).baseUrl("http://localhost:" + port).build();
    }

    private Object[] uriVariables(String path) {
        return path.contains(OBTAINED_ID) ? new Object[] {obtainedId()} : new Object[0];
    }
}
