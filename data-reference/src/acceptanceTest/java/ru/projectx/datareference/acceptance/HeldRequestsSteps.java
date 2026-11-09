package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import io.cucumber.java.ru.Тогда;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import ru.projectx.datareference.config.ReferenceApiProperties;
import ru.projectx.datareference.config.restread.RestReadPoolProperties;

public class HeldRequestsSteps {

    private static final int BEFORE_DATABASE_RESTORE = 20_000;
    private static final int FRACTION_OF_THE_SHORTEST_WAIT = 2;

    private final HeldRequests held;
    private final ContractsService contracts;
    private final ReferenceApiProperties api;
    private final RestReadPoolProperties restReadPool;

    public HeldRequestsSteps(HeldRequests held, ContractsService contracts, ReferenceApiProperties api,
            RestReadPoolProperties restReadPool) {

        this.held = held;
        this.contracts = contracts;
        this.api = api;
        this.restReadPool = restReadPool;
    }

    @Дано("столько запросов сервиса Договоров/Заявок {string}, сколько Дата-референс обрабатывает одновременно, "
            + "удерживаются внутри обработки")
    public void requestsHeldUpToTheLimit(String path) {
        held.hold(path, api.maxConcurrentRequests());
    }

    @Когда("удержанные запросы отпущены")
    public void released() throws InterruptedException, ExecutionException, TimeoutException {
        held.release();
    }

    @Когда("удержанные запросы отпущены отказом базы")
    public void releasedWithDatabaseFailure() throws InterruptedException, ExecutionException, TimeoutException {
        held.releaseWithDatabaseFailure();
    }

    @Тогда("каждый удержанный запрос получил ответ {int}")
    public void everyHeldRequestAnswered(int status)
            throws InterruptedException, ExecutionException, TimeoutException {

        assertThat(held.answers()).as("ответы на удержанные запросы")
                .isNotEmpty()
                .allSatisfy(answer -> assertThat(answer.status()).as("статус ответа").isEqualTo(status));
    }

    @Тогда("каждый удержанный запрос получил ответ {int} с кодом {string}")
    public void everyHeldRequestAnsweredWithCode(int status, String code)
            throws InterruptedException, ExecutionException, TimeoutException {

        assertThat(held.answers()).as("ответы на удержанные запросы")
                .isNotEmpty()
                .allSatisfy(answer -> {
                    assertThat(answer.status()).as("статус ответа").isEqualTo(status);
                    assertThat(answer.errorCode()).as("error.code ответа").isEqualTo(code);
                });
    }

    @Тогда("Дата-референс ответил сразу — много раньше, чем истекает ожидание соединения с базой")
    public void answeredImmediately() {
        Duration shortestWait = restReadPool.connectionTimeout().dividedBy(FRACTION_OF_THE_SHORTEST_WAIT);
        assertThat(contracts.lastResponse().elapsed())
                .as("время ответа против ожидания соединения %s", restReadPool.connectionTimeout())
                .isLessThan(shortestWait);
    }

    @After(order = BEFORE_DATABASE_RESTORE)
    public void releaseHeldRequests() throws InterruptedException, ExecutionException, TimeoutException {
        held.releaseIfHolding();
    }
}
