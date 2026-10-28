package ru.projectx.datareference.service.integration;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.OneCQueueProperties;

@Component
public class OneCQueueConsumers {

    private final OneCQueueProperties queues;
    private final OneCConsumerExpectation expectation;
    private final OneCConsumerSubscriptions subscriptions;

    OneCQueueConsumers(OneCQueueProperties queues, OneCConsumerExpectation expectation,
            OneCConsumerSubscriptions subscriptions) {

        this.queues = queues;
        this.expectation = expectation;
        this.subscriptions = subscriptions;
    }

    public List<String> queuesWithoutConsumer() {
        if (!expectation.consumersExpected()) {
            return List.of();
        }
        return Arrays.stream(OneCQueue.values())
                .map(queue -> queue.nameIn(queues))
                .filter(queue -> !subscriptions.subscribed(queue))
                .sorted()
                .toList();
    }
}
