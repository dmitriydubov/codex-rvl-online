package ru.projectx.datareference.acceptance;

import io.cucumber.spring.ScenarioScope;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.client.ExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
public class ContractsService {

    private static final String OBTAINED_ID = "{id}";
    private static final String PATH_ROOT = "/";
    private static final String QUERY_START = "?";
    private static final String PARAMETER_SEPARATOR = "&";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final JdkClientHttpRequestFactory requests;
    private final RestTestClient client;
    private final RestTestClient monitoringClient;
    private @Nullable ServiceResponse lastResponse;
    private @Nullable String obtainedId;

    public ContractsService(@LocalServerPort int port, @LocalManagementPort int managementPort) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        this.requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(READ_TIMEOUT);
        this.client = clientOn(requests, port);
        this.monitoringClient = clientOn(requests, managementPort);
    }

    public ServiceResponse request(String path) {
        return exchange(HttpMethod.GET, path, headers -> { });
    }

    public ServiceResponse monitor(String path) {
        return monitorOn(monitoringClient, path);
    }

    public ServiceResponse requestAt(int port, String path) {
        return exchange(clientOn(requests, port), HttpMethod.GET, path, headers -> { });
    }

    public ServiceResponse monitorAt(int managementPort, String path) {
        return monitorOn(clientOn(requests, managementPort), path);
    }

    public ServiceResponse request(String path, String requestId) {
        return exchange(HttpMethod.GET, path, headers -> headers.set(ServiceResponse.REQUEST_ID, requestId));
    }

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

        return exchange(client, method, path, headers, parameterValues);
    }

    private ServiceResponse exchange(RestTestClient target,
            HttpMethod method, String path, Consumer<HttpHeaders> headers, Object... parameterValues) {

        requireAbsolutePath(path);
        Object[] variables = Stream.concat(Arrays.stream(uriVariables(path)), Arrays.stream(parameterValues))
                .toArray();
        return timed(() -> target.method(method).uri(path, variables).headers(headers).exchange().returnResult());
    }

    private ServiceResponse monitorOn(RestTestClient target, String path) {
        requireAbsolutePath(path);
        return timed(() -> target.get().uri(path).exchange().returnResult());
    }

    private ServiceResponse timed(Supplier<ExchangeResult> exchange) {
        long started = System.nanoTime();
        ExchangeResult result = exchange.get();
        lastResponse = ServiceResponse.of(result, Duration.ofNanos(System.nanoTime() - started));
        return lastResponse;
    }

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
