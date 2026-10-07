package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import io.cucumber.java.ru.Тогда;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Всплеск REST-чтений: соседи шлют одновременно больше запросов, чем у Дата-референса соединений с базой
 * ({@link RestReadLoad}).
 */
public class RestReadLoadSteps {

    /** Нагрузка останавливается раньше, чем снимается блокировка базы: её ответы получены под блокировкой. */
    private static final int BEFORE_LOCK_RELEASE = 20_000;

    private final RestReadLoad load;

    public RestReadLoadSteps(RestReadLoad load) {
        this.load = load;
    }

    @Дано("сервис Договоров/Заявок непрерывно запрашивает {string} — одновременно больше запросов, чем у "
            + "Дата-референса соединений с базой")
    public void requestsContinuously(String path) {
        load.start(path);
    }

    @Когда("сервис Договоров/Заявок перестал запрашивать {string}")
    public void stoppedRequesting(String path) throws InterruptedException, ExecutionException, TimeoutException {
        assertThat(load.path()).as("путь под нагрузкой").isEqualTo(path);
        load.stop();
    }

    @Тогда("каждый запрос {string} под нагрузкой получил ответ {int} с кодом {string}")
    public void everyLoadAnswer(String path, int status, String code) {
        assertThat(load.path()).as("путь под нагрузкой").isEqualTo(path);
        assertThat(load.answers()).as("ответы на запросы %s под нагрузкой", path)
                .isNotEmpty()
                .allSatisfy(answer -> {
                    assertThat(answer.status()).as("статус ответа").isEqualTo(status);
                    assertThat(answer.errorCode()).as("error.code ответа").isEqualTo(code);
                });
    }

    @After(order = BEFORE_LOCK_RELEASE)
    public void stopLoad() throws InterruptedException, ExecutionException, TimeoutException {
        load.stopIfRunning();
    }
}
