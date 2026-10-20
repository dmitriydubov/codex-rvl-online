package ru.projectx.datareference.service.integration;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.integration.leader.event.AbstractLeaderEvent;
import org.springframework.integration.leader.event.OnGrantedEvent;
import org.springframework.integration.leader.event.OnRevokedEvent;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.service.leadership.LeaderLock;

@Component
class OneCIngestLeadership implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OneCIngestLeadership.class);

    private static final String THREAD_PREFIX = "onec-role-";
    private static final int ONE_THREAD = 1;
    private static final Duration FINISH_ROLE_CHANGE = Duration.ofSeconds(30);

    private final OneCConsumerPause consumers;
    private final LeaderLock lock;
    private final ThreadPoolTaskExecutor roleChanges = new ThreadPoolTaskExecutorBuilder()
            .corePoolSize(ONE_THREAD)
            .maxPoolSize(ONE_THREAD)
            .threadNamePrefix(THREAD_PREFIX)
            .awaitTermination(true)
            .awaitTerminationPeriod(FINISH_ROLE_CHANGE)
            .build();

    OneCIngestLeadership(OneCConsumerPause consumers, LeaderLock lock) {
        this.consumers = consumers;
        this.lock = lock;
        roleChanges.initialize();
    }

    @EventListener
    void granted(OnGrantedEvent event) {
        applyFor(event, consumers::startUnlessPaused);
    }

    @EventListener
    void revoked(OnRevokedEvent event) {
        applyFor(event, consumers::stopConsumers);
    }

    @Override
    public void close() {
        roleChanges.shutdown();
    }

    private void applyFor(AbstractLeaderEvent event, Runnable roleChange) {
        if (!lock.role().equals(event.getRole())) {
            return;
        }
        try {
            roleChanges.execute(() -> apply(roleChange));
        }
        catch (TaskRejectedException stopping) {
            log.debug("смена роли {} не применена: приложение останавливается", event.getRole());
        }
    }

    private static void apply(Runnable roleChange) {
        try {
            roleChange.run();
        }
        catch (RuntimeException failed) {
            log.error("смена роли ведущего не применена к потребителям 1С:УСК: {}", failed.getClass().getName());
        }
    }
}
