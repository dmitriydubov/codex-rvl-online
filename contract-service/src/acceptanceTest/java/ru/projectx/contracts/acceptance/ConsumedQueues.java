package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.awaitility.Awaitility;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.service.integration.InboxConsumerSubscriptions;
import ru.projectx.contracts.service.integration.InboxQueueConsumers;

@Component
public class ConsumedQueues {

    private static final Duration PATIENCE = Duration.ofSeconds(60);

    private final InboxQueueConsumers consumers;
    private final RabbitListenerEndpointRegistry containers;
    private final InboxConsumerSubscriptions subscriptions;

    public ConsumedQueues(InboxQueueConsumers consumers, RabbitListenerEndpointRegistry containers,
            InboxConsumerSubscriptions subscriptions) {
        this.consumers = consumers;
        this.containers = containers;
        this.subscriptions = subscriptions;
    }

    void awaitEveryConsumer() {
        Awaitility.await().atMost(PATIENCE).untilAsserted(() ->
                assertThat(consumers.queuesWithoutConsumer()).as("очереди без подписанного потребителя").isEmpty());
    }

    void awaitSubscribedOrStopped() {
        Awaitility.await().atMost(PATIENCE).until(this::everyRunningContainerSubscribedOrStopped);
    }

    void startStopped() {
        containers.getListenerContainerIds().stream()
                .map(containers::getListenerContainer)
                .filter(container -> !container.isRunning())
                .forEach(container -> container.start());
    }

    private boolean everyRunningContainerSubscribedOrStopped() {
        return containers.getListenerContainerIds().stream()
                .allMatch(queue -> !containers.getListenerContainer(queue).isRunning() || subscriptions.subscribed(queue));
    }
}
