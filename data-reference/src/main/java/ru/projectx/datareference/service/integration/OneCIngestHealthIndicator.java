package ru.projectx.datareference.service.integration;

import java.util.List;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("onecIngestHealthIndicator")
public class OneCIngestHealthIndicator implements HealthIndicator {

    private static final String QUEUES_WITHOUT_CONSUMER = "queuesWithoutConsumer";

    private final OneCConsumerPause pause;
    private final OneCQueueConsumers consumers;

    public OneCIngestHealthIndicator(OneCConsumerPause pause, OneCQueueConsumers consumers) {
        this.pause = pause;
        this.consumers = consumers;
    }

    @Override
    public Health health() {
        return pause.paused()
                .map(OneCIngestHealthIndicator::pausedSince)
                .orElseGet(this::consumersHealth);
    }

    private Health consumersHealth() {
        List<String> withoutConsumer = consumers.queuesWithoutConsumer();
        if (withoutConsumer.isEmpty()) {
            return Health.up().build();
        }
        return Health.down().withDetail(QUEUES_WITHOUT_CONSUMER, withoutConsumer).build();
    }

    private static Health pausedSince(OneCConsumerPause.Paused paused) {
        return Health.down()
                .withDetail("sqlstate", paused.outage().sqlState())
                .withDetail("failure", paused.outage().failureType())
                .withDetail("since", paused.since().toString())
                .withDetail("queues", paused.queues())
                .build();
    }
}
