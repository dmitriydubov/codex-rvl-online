package ru.projectx.contracts.service.integration.consumer;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InboxQueueConsumers {

    private final InboxQueues queues;
    private final InboxQueueContainers containers;
    private final InboxConsumerSubscriptions subscriptions;

    InboxQueueConsumers(InboxQueues queues, InboxQueueContainers containers, InboxConsumerSubscriptions subscriptions) {
        this.queues = queues;
        this.containers = containers;
        this.subscriptions = subscriptions;
    }

    public List<String> queuesWithoutConsumer() {
        if (!containers.consumersExpected()) {
            return List.of();
        }
        return queues.consumed().stream()
                .filter(queue -> !subscriptions.subscribed(queue))
                .sorted()
                .toList();
    }
}
