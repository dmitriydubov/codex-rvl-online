package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.cucumber.spring.ScenarioScope;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.RestReadDataSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Всплеск запросов соседа: одновременно больше запросов, чем соединений во всех пулах сервиса, и каждый после ответа
 * сразу шлёт следующий. Начало нагрузки видно по счётчикам пула REST-чтений, а не по времени: пул занят целиком,
 * и следующие запросы ждут соединения.
 */
@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
public class RestReadLoad {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration SATURATION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(30);
    private static final String OBTAINED_ID = "{id}";
    private static final String ERROR_CODE = "/error/code";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    private final JsonMapper json = JsonMapper.shared();
    private final Queue<Answer> answers = new ConcurrentLinkedQueue<>();
    private final List<Future<Void>> requesters = new ArrayList<>();
    private final ContractsService contracts;
    private final RestReadDataSource pools;
    private final int port;
    private volatile boolean running;
    private Optional<String> path = Optional.empty();
    private Optional<ExecutorService> executor = Optional.empty();

    public RestReadLoad(ContractsService contracts, DataSource dataSource, @LocalServerPort int port)
            throws SQLException {

        this.contracts = contracts;
        this.pools = dataSource.unwrap(RestReadDataSource.class);
        this.port = port;
    }

    /** Запускает нагрузку на {@code path} и ждёт, пока она займёт весь пул REST-чтений. */
    public void start(String path) {
        URI target = URI.create("http://localhost:" + port + path.replace(OBTAINED_ID, contracts.obtainedId()));
        ExecutorService requests = Executors.newVirtualThreadPerTaskExecutor();
        this.path = Optional.of(path);
        this.executor = Optional.of(requests);
        running = true;
        for (int requester = 0; requester < concurrency(); requester++) {
            requesters.add(requests.submit(() -> requestUntilStopped(target)));
        }
        await().atMost(SATURATION_TIMEOUT).until(this::restReadPoolSaturated);
    }

    public String path() {
        return path.orElseThrow(() -> new IllegalStateException("сценарий не запускал нагрузку"));
    }

    /** Останавливает нагрузку и ждёт ответов на все отправленные запросы. */
    public void stop() throws InterruptedException, ExecutionException, TimeoutException {
        running = false;
        ExecutorService requests = executor.orElseThrow(() -> new IllegalStateException("нагрузка не запущена"));
        executor = Optional.empty();
        requests.shutdown();
        assertThat(requests.awaitTermination(STOP_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                .as("ответы на запросы нагрузки за %s", STOP_TIMEOUT).isTrue();
        for (Future<Void> requester : requesters) {
            requester.get(0, TimeUnit.SECONDS);
        }
    }

    /** Нагрузка, которую сценарий не остановил сам, останавливается в конце сценария. */
    public void stopIfRunning() throws InterruptedException, ExecutionException, TimeoutException {
        if (executor.isPresent()) {
            stop();
        }
    }

    public List<Answer> answers() {
        return List.copyOf(answers);
    }

    private int concurrency() {
        return pools.applicationPool().getMaximumPoolSize() + pools.restReadPool().getMaximumPoolSize() + 1;
    }

    private boolean restReadPoolSaturated() {
        HikariDataSource restRead = pools.restReadPool();
        HikariPoolMXBean pool = restRead.getHikariPoolMXBean();
        return pool != null && pool.getActiveConnections() == restRead.getMaximumPoolSize()
                && pool.getThreadsAwaitingConnection() > 0;
    }

    private Void requestUntilStopped(URI target) throws IOException, InterruptedException {
        while (running) {
            answers.add(request(target));
        }
        return null;
    }

    private Answer request(URI target) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(target).timeout(REQUEST_TIMEOUT).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return new Answer(response.statusCode(), json.readTree(response.body()).at(ERROR_CODE).asString(""));
    }

    /** Ответ на запрос нагрузки; {@code errorCode} пустой, если в теле нет {@code error.code}. */
    public record Answer(int status, String errorCode) {
    }
}
