package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.cucumber.java.After;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import java.time.Duration;
import javax.sql.DataSource;
import ru.projectx.datareference.service.integration.OneCConsumerPause;

/**
 * Недоступность базы Дата-референса в сценарии: пул соединений сервиса отказывает на каждом запросе соединения
 * с заданным SQLSTATE ({@link SwitchableDataSource}), пока база «снова не принимает соединения». Проверку
 * доступности, которая в работающем сервисе идёт по расписанию, сценарий вызывает явно: расписания в приёмке
 * выключены.
 *
 * <p>После сценария база включается обратно, а проверка возвращает потребителей, чтобы следующий сценарий
 * начинал с работающим приёмом даже после провала. Этот хук идёт после хука снятия блокировки (у него порядок
 * ниже, а хуки «после» идут от большего порядка к меньшему): пока таблицы заблокированы, проверка потребителей
 * не вернёт.
 */
public class DatabaseAvailabilitySteps {

    private static final int AFTER_LOCK_RELEASE = 100;
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(30);

    private final SwitchableDataSource dataSource;
    private final OneCConsumerPause pause;

    public DatabaseAvailabilitySteps(DataSource dataSource, OneCConsumerPause pause) {
        assertThat(dataSource).as("пул соединений стенда с выключателем").isInstanceOf(SwitchableDataSource.class);
        this.dataSource = (SwitchableDataSource) dataSource;
        this.pause = pause;
    }

    @Дано("каждое соединение с базой Дата-референса отказывает с SQLSTATE {string}")
    public void connectionsFail(String sqlState) {
        dataSource.failConnectionsWith(sqlState);
    }

    @Когда("база Дата-референса снова принимает соединения")
    public void connectionsRestored() {
        dataSource.restore();
    }

    /**
     * Проверка — как её запустило бы расписание: к этому моменту остановка потребителей уже завершена (в
     * работающем сервисе между отказом и проверкой проходят секунды, здесь ожидание явное).
     */
    @Когда("Дата-референс проверяет доступность базы")
    public void storageChecked() {
        await().atMost(STOP_TIMEOUT).until(pause::consumersStopped);
        pause.resumeIfStorageAvailable();
    }

    @After(order = AFTER_LOCK_RELEASE)
    public void restoreAndResume() {
        dataSource.restore();
        storageChecked();
    }
}
