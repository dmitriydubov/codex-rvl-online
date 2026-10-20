package ru.projectx.datareference.service.integration;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.OneCQueueProperties;

@Component
public class OneCQueueConsumers {

    private final OneCQueueProperties queues;
    private final OneCQueueContainers containers;
    private final OneCConsumerSubscriptions subscriptions;

    OneCQueueConsumers(OneCQueueProperties queues, OneCQueueContainers containers,
            OneCConsumerSubscriptions subscriptions) {

        this.queues = queues;
        this.containers = containers;
        this.subscriptions = subscriptions;
    }

    public List<String> queuesWithoutConsumer() {
        if (!containers.consumersExpected()) {
            return List.of();
        }
        return Arrays.stream(OneCQueue.values())
                .map(queue -> queue.nameIn(queues))
                .filter(queue -> !subscriptions.subscribed(queue))
                .sorted()
                .toList();
    }
}
