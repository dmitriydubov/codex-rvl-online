package ru.projectx.datareference.service.integration;

import java.util.List;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.service.leadership.BackgroundLeadership;

@Component("onecIngestHealthIndicator")
public class OneCIngestHealthIndicator implements HealthIndicator {

    private static final String QUEUES_WITHOUT_CONSUMER = "queuesWithoutConsumer";
    private static final String ROLE = "role";
    private static final String LEADER = "leader";
    private static final String STANDBY = "standby";

    private final OneCConsumerPause pause;
    private final OneCQueueConsumers consumers;
    private final BackgroundLeadership leadership;

    public OneCIngestHealthIndicator(OneCConsumerPause pause, OneCQueueConsumers consumers,
            BackgroundLeadership leadership) {

        this.pause = pause;
        this.consumers = consumers;
        this.leadership = leadership;
    }

    @Override
    public Health health() {
        return pause.paused()
                .map(this::pausedSince)
                .orElseGet(this::consumersHealth);
    }

    private Health consumersHealth() {
        List<String> withoutConsumer = consumers.queuesWithoutConsumer();
        if (withoutConsumer.isEmpty()) {
            return withRole(Health.up()).build();
        }
        return withRole(Health.down()).withDetail(QUEUES_WITHOUT_CONSUMER, withoutConsumer).build();
    }

    private Health pausedSince(OneCConsumerPause.Paused paused) {
        return withRole(Health.down())
                .withDetail("sqlstate", paused.outage().sqlState())
                .withDetail("failure", paused.outage().failureType())
                .withDetail("since", paused.since().toString())
                .withDetail("queues", paused.queues())
                .build();
    }

    private Health.Builder withRole(Health.Builder health) {
        return health.withDetail(ROLE, leadership.leader() ? LEADER : STANDBY);
    }
}
