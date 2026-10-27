package ru.projectx.contracts.acceptance;

import io.cucumber.java.After;
import ru.projectx.contracts.service.integration.InboxConsumerPause;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;

public class ConsumerRestoreHook {

    private static final int AFTER_QUEUES_REDECLARED = 5000;

    private final ConsumedQueues consumers;
    private final InboxConsumerPause pause;
    private final BackgroundLeadership leadership;

    public ConsumerRestoreHook(ConsumedQueues consumers, InboxConsumerPause pause, BackgroundLeadership leadership) {
        this.consumers = consumers;
        this.pause = pause;
        this.leadership = leadership;
    }

    @After(order = AFTER_QUEUES_REDECLARED)
    public void restartConsumersStoppedByALostQueue() {
        if (leadership.leader() && pause.paused().isEmpty()) {
            consumers.startStopped();
        }
    }
}
