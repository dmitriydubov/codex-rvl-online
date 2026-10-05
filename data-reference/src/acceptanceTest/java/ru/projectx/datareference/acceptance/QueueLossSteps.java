package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.cucumber.java.After;
import io.cucumber.java.ru.Когда;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import ru.projectx.datareference.service.integration.OneCConsumerSubscriptions;

/**
 * Очередь 1С:УСК пропала на брокере: стенд удаляет её, как это сделала бы сторона 1С:УСК.
 *
 * <p>Контекст приёмки общий на все фичи, поэтому после сценария стенд возвращает всё как было — так же, как
 * перезапуск сервиса: объявляет очередь заново и запускает её контейнер, если тот остановился, и ждёт, пока
 * брокер подтвердит подписку. Этот хук идёт последним из хуков «после» (у него наименьший порядок): к нему
 * база уже доступна и пауза снята, и запуск контейнера никто не отменит.
 */
public class QueueLossSteps {

    private static final int AFTER_RESUME = 50;
    /** Запас на повторы потребителя, который ещё ищет очередь, и на подтверждение подписки. */
    private static final Duration RESTORE_TIMEOUT = Duration.ofSeconds(60);

    private final OneCBroker broker;
    private final OneCStandQueues standQueues;
    private final RabbitListenerEndpointRegistry containers;
    private final OneCConsumerSubscriptions subscriptions;
    private final List<String> deleted = new ArrayList<>();

    public QueueLossSteps(OneCBroker broker, OneCStandQueues standQueues, RabbitListenerEndpointRegistry containers,
            OneCConsumerSubscriptions subscriptions) {

        this.broker = broker;
        this.standQueues = standQueues;
        this.containers = containers;
        this.subscriptions = subscriptions;
    }

    @Когда("очередь {string} удалена на брокере")
    public void queueDeleted(String stream) {
        String queue = broker.queueOf(stream);
        deleted.add(queue);
        standQueues.delete(queue);
    }

    @After(order = AFTER_RESUME)
    public void restoreDeletedQueues() {
        deleted.forEach(this::restore);
    }

    private void restore(String queue) {
        standQueues.declare(queue);
        MessageListenerContainer container = containers.getListenerContainer(queue);
        await().atMost(RESTORE_TIMEOUT).ignoreExceptions().untilAsserted(() -> {
            startIfStopped(container);
            assertThat(subscriptions.subscribed(queue)).as("потребитель очереди %s снова подписан", queue).isTrue();
        });
    }

    private static void startIfStopped(MessageListenerContainer container) {
        if (!container.isRunning()) {
            container.start();
        }
    }
}
