package ru.projectx.contracts.support;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.springframework.context.SmartLifecycle;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;

public class LeadershipAwait implements SmartLifecycle {

    private static final Duration LEADERSHIP_TIMEOUT = Duration.ofSeconds(30);
    private static final int AFTER_LEADER_INITIATOR = Integer.MAX_VALUE - 900;

    private final BackgroundLeadership leadership;
    private volatile boolean running;

    public LeadershipAwait(BackgroundLeadership leadership) {
        this.leadership = leadership;
    }

    @Override
    public void start() {
        await().atMost(LEADERSHIP_TIMEOUT).until(leadership::leader);
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return AFTER_LEADER_INITIATOR;
    }
}
