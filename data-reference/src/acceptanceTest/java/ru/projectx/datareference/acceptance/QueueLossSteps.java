package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.cucumber.java.After;
import io.cucumber.java.ru.Когда;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import ru.projectx.datareference.service.integration.OneCConsumerRestart;
import ru.projectx.datareference.service.integration.OneCConsumerSubscriptions;
import ru.projectx.datareference.service.leadership.BackgroundLeadership;

public class QueueLossSteps {

    private static final int AFTER_RESUME = 50;
    private static final Duration CONTAINER_STOP_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration RESTORE_TIMEOUT = Duration.ofSeconds(60);

    private final OneCBroker broker;
    private final OneCStandQueues standQueues;
    private final RabbitListenerEndpointRegistry containers;
    private final OneCConsumerSubscriptions subscriptions;
    private final OneCConsumerRestart restart;
    private final BackgroundLeadership leadership;
    private final List<String> deleted = new ArrayList<>();

    public QueueLossSteps(OneCBroker broker, OneCStandQueues standQueues, RabbitListenerEndpointRegistry containers,
            OneCConsumerSubscriptions subscriptions, OneCConsumerRestart restart, BackgroundLeadership leadership) {

        this.broker = broker;
        this.standQueues = standQueues;
        this.containers = containers;
        this.subscriptions = subscriptions;
        this.restart = restart;
        this.leadership = leadership;
    }

    @Когда("очередь {string} удалена на брокере")
    public void queueDeleted(String stream) {
        String queue = broker.queueOf(stream);
        deleted.add(queue);
        standQueues.delete(queue);
        await().atMost(CONTAINER_STOP_TIMEOUT)
                .alias("потребитель очереди " + queue + " остановился, не найдя её")
                .until(() -> !containers.getListenerContainer(queue).isRunning());
    }

    @Когда("очередь {string} снова объявлена на брокере")
    public void queueDeclaredAgain(String stream) {
        standQueues.declare(broker.queueOf(stream));
    }

    @After(order = AFTER_RESUME)
    public void restoreDeletedQueues() {
        deleted.forEach(this::restore);
    }

    private void restore(String queue) {
        standQueues.declare(queue);
        restart.restartStopped();
        await().atMost(RESTORE_TIMEOUT).untilAsserted(() -> assertThat(subscribedOrStandby(queue))
                .as("потребитель очереди %s снова подписан, если экземпляр ведущий", queue).isTrue());
    }

    private boolean subscribedOrStandby(String queue) {
        return !leadership.leader() || subscriptions.subscribed(queue);
    }
}
