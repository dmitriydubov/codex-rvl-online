package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
public class HeldRequests {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration HOLD_TIMEOUT = Duration.ofSeconds(30);
    private static final String OBTAINED_ID = "{id}";
    private static final String ERROR_CODE = "/error/code";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    private final JsonMapper json = JsonMapper.shared();
    private final ExecutorService requesters = Executors.newVirtualThreadPerTaskExecutor();
    private final ContractsService contracts;
    private final HeldReadsDataSource reads;
    private final int port;
    private List<Future<Answer>> inFlight = List.of();

    public HeldRequests(ContractsService contracts, DataSource dataSource, @LocalServerPort int port)
            throws SQLException {

        this.contracts = contracts;
        this.reads = dataSource.unwrap(HeldReadsDataSource.class);
        this.port = port;
    }

    public void hold(String path, int count) {
        URI target = URI.create("http://localhost:" + port + path.replace(OBTAINED_ID, contracts.obtainedId()));
        reads.holdInternalApiReads();
        inFlight = IntStream.range(0, count)
                .mapToObj(request -> requesters.submit(() -> request(target)))
                .toList();
        await().atMost(HOLD_TIMEOUT).until(() -> reads.held() == count);
        assertThat(inFlight).as("удержанные запросы ещё без ответа").noneMatch(Future::isDone);
    }

    public void release() throws InterruptedException, ExecutionException, TimeoutException {
        reads.release();
        awaitAnswers();
    }

    public void releaseWithDatabaseFailure() throws InterruptedException, ExecutionException, TimeoutException {
        reads.releaseWithConnectionFailure();
        awaitAnswers();
    }

    public void releaseIfHolding() throws InterruptedException, ExecutionException, TimeoutException {
        release();
        requesters.close();
    }

    public List<Answer> answers() throws InterruptedException, ExecutionException, TimeoutException {
        List<Answer> answers = new ArrayList<>();
        for (Future<Answer> request : inFlight) {
            answers.add(request.get(REQUEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS));
        }
        return answers;
    }

    private void awaitAnswers() throws InterruptedException, ExecutionException, TimeoutException {
        assertThat(answers()).as("ответы на удержанные запросы").hasSize(inFlight.size());
    }

    private Answer request(URI target) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(target).timeout(REQUEST_TIMEOUT).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return new Answer(response.statusCode(), json.readTree(response.body()).at(ERROR_CODE).asString(""));
    }

    public record Answer(int status, String errorCode) {
    }
}
