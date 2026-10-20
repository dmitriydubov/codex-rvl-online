package ru.projectx.datareference.acceptance;

import static org.awaitility.Awaitility.await;

import io.cucumber.java.After;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.function.Supplier;
import tools.jackson.databind.JsonNode;

public class SecondInstanceSteps {

    private static final Duration ROLE_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    private final SecondInstance second;
    private final ContractsService contracts;

    public SecondInstanceSteps(SecondInstance second, ContractsService contracts) {
        this.second = second;
        this.contracts = contracts;
    }

    @Дано("запущен второй экземпляр Дата-референса на той же базе")
    public void secondInstanceStarted() {
        second.start();
    }

    @Когда("сервис Договоров/Заявок запрашивает у второго экземпляра {string}")
    public void requestsSecondInstance(String path) {
        contracts.requestAt(second.port(), path);
    }

    @Когда("мониторинг запрашивает у второго экземпляра {string}, пока поле {string} ответа не станет равно {string}")
    public void monitoringRequestsSecondInstanceUntil(String path, String field, String expected) {
        awaitField(ROLE_TIMEOUT, () -> contracts.monitorAt(second.managementPort(), path), field, expected);
    }

    @Когда("сервис Договоров/Заявок запрашивает у второго экземпляра {string}, пока поле {string} ответа не станет равно "
            + "{string}, но не дольше {int} секунд")
    public void requestsSecondInstanceUntil(String path, String field, String expected, int seconds) {
        awaitField(Duration.ofSeconds(seconds), () -> contracts.requestAt(second.port(), path), field, expected);
    }

    @Когда("связь второго экземпляра Дата-референса с базой оборвалась")
    public void secondInstanceLostDatabase() {
        second.cutOffFromDatabase();
    }

    @Когда("связь второго экземпляра Дата-референса с базой восстановилась")
    public void secondInstanceRegainedDatabase() {
        second.allowConnections();
    }

    @After
    public void stopSecondInstance() {
        second.stop();
    }

    private static void awaitField(Duration limit, Supplier<ServiceResponse> request, String field, String expected) {
        await().atMost(limit).pollInterval(POLL_INTERVAL)
                .until(() -> request.get().field(field), value -> equalTo(value, expected));
    }

    private static boolean equalTo(JsonNode value, String expected) {
        if (value.isNumber()) {
            return value.decimalValue().compareTo(new BigDecimal(expected)) == 0;
        }
        return value.isString() && expected.equals(value.asString());
    }
}
