package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.cucumber.java.After;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import java.time.Duration;
import javax.sql.DataSource;
import ru.projectx.contracts.service.integration.InboxConsumerPause;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;

public class DatabaseAvailabilitySteps {

    private static final int AFTER_LOCK_RELEASE = 100;
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(45);

    private final SwitchableDataSource dataSource;
    private final InboxConsumerPause pause;
    private final BackgroundLeadership leadership;
    private final ConsumedQueues consumers;

    public DatabaseAvailabilitySteps(DataSource dataSource, InboxConsumerPause pause, BackgroundLeadership leadership,
            ConsumedQueues consumers) {
        assertThat(dataSource).as("пул соединений стенда с выключателем").isInstanceOf(SwitchableDataSource.class);
        this.dataSource = (SwitchableDataSource) dataSource;
        this.pause = pause;
        this.leadership = leadership;
        this.consumers = consumers;
    }

    @Дано("каждое соединение с базой Договоров отказывает с SQLSTATE {string}")
    public void connectionsFail(String sqlState) {
        dataSource.failConnectionsWith(sqlState);
    }

    @Когда("база Договоров снова принимает соединения")
    public void connectionsRestored() {
        dataSource.restore();
    }

    @Когда("Договоры проверяют доступность базы")
    public void storageChecked() {
        await().atMost(STOP_TIMEOUT).until(pause::consumersStopped);
        pause.resumeIfStorageAvailable();
        if (pause.paused().isEmpty() && leadership.leader()) {
            consumers.awaitSubscribedOrStopped();
        }
    }

    @After(order = AFTER_LOCK_RELEASE)
    public void restoreAndResume() {
        dataSource.restore();
        storageChecked();
    }
}
