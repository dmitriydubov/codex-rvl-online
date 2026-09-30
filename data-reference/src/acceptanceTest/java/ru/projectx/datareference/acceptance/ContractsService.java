package ru.projectx.datareference.acceptance;

import io.cucumber.spring.ScenarioScope;
import java.util.Optional;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Сервис Договоров в роли потребителя: настоящие HTTP-запросы к запущенному Дата-референсу
 * ({@code RANDOM_PORT}) и то, что потребитель помнит между шагами сценария, —
 * последний ответ и id, полученный на шаге «Дано».
 *
 * <p>Клиент — {@link RestTestClient#bindToServer()} из spring-test: автонастройка Boot
 * для него ({@code spring-boot-resttestclient}) — отдельный модуль, которого нет
 * на classpath и в Nexus контура.
 */
@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
public class ContractsService {

    /** Место id, полученного на шаге «Дано», в пути запроса сценария. */
    private static final String OBTAINED_ID = "{id}";

    private final RestTestClient client;
    private @Nullable ServiceResponse lastResponse;
    private @Nullable String obtainedId;

    public ContractsService(@LocalServerPort int port) {
        this.client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
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
        lastResponse = ServiceResponse.of(client.get().uri(withObtainedId(path)).headers(headers).exchange().returnResult());
        return lastResponse;
    }

    private String withObtainedId(String path) {
        return path.contains(OBTAINED_ID) ? path.replace(OBTAINED_ID, obtainedId()) : path;
    }
}
