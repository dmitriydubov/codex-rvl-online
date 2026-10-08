package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.cucumber.java.After;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import ru.projectx.datareference.service.integration.OneCConsumerPause;
import ru.projectx.datareference.service.integration.OneCConsumerSubscriptions;

/**
 * Недоступность базы в сценарии: пул сервиса отказывает каждому соединению с заданным SQLSTATE, а проверку доступности
 * шаг вызывает явно, потому что расписания в приёмке выключены. После сценария приём возвращается даже после провала;
 * хук идёт после снятия блокировки таблиц (хуки «после» идут от большего порядка), иначе потребители не вернутся.
 */
public class DatabaseAvailabilitySteps {

    private static final int AFTER_LOCK_RELEASE = 100;
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(30);
    /** Запас на подтверждение подписок и на остановку контейнера, который после повторов не нашёл свою очередь. */
    private static final Duration SUBSCRIBE_TIMEOUT = Duration.ofSeconds(30);

    private final SwitchableDataSource dataSource;
    private final OneCConsumerPause pause;
    private final RabbitListenerEndpointRegistry containers;
    private final OneCConsumerSubscriptions subscriptions;

    public DatabaseAvailabilitySteps(DataSource dataSource, OneCConsumerPause pause,
            RabbitListenerEndpointRegistry containers, OneCConsumerSubscriptions subscriptions) {

        assertThat(dataSource).as("пул соединений стенда с выключателем").isInstanceOf(SwitchableDataSource.class);
        this.dataSource = (SwitchableDataSource) dataSource;
        this.pause = pause;
        this.containers = containers;
        this.subscriptions = subscriptions;
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
     * В работающем сервисе между отказом, проверкой и запросом мониторинга проходят секунды, здесь ожидания явные.
     * Подтверждение подписки приходит из потока клиента брокера позже, чем запуск контейнера возвращает управление,
     * и без ожидания следующий шаг иногда застал бы потребителя за миг до подписки.
     */
    @Когда("Дата-референс проверяет доступность базы")
    public void storageChecked() {
        await().atMost(STOP_TIMEOUT).until(pause::consumersStopped);
        pause.resumeIfStorageAvailable();
        await().atMost(SUBSCRIBE_TIMEOUT).until(this::everyRunningContainerSubscribedOrStopped);
    }

    private boolean everyRunningContainerSubscribedOrStopped() {
        return containers.getListenerContainerIds().stream()
                .allMatch(queue -> !containers.getListenerContainer(queue).isRunning() || subscriptions.subscribed(queue));
    }

    @After(order = AFTER_LOCK_RELEASE)
    public void restoreAndResume() {
        dataSource.restore();
        storageChecked();
    }
}
