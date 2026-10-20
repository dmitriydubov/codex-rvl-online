package ru.projectx.datareference.service.integration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.event.EventListener;
import org.springframework.integration.leader.event.OnGrantedEvent;
import org.springframework.integration.leader.event.OnRevokedEvent;
import org.springframework.stereotype.Component;

@Component
class OneCIngestLeadership implements AutoCloseable {

    private static final String THREAD_NAME = "onec-role-";

    private final OneCConsumerPause consumers;
    private final ExecutorService roleChanges =
            Executors.newSingleThreadExecutor(Thread.ofPlatform().name(THREAD_NAME, 0).daemon().factory());

    OneCIngestLeadership(OneCConsumerPause consumers) {
        this.consumers = consumers;
    }

    @EventListener(OnGrantedEvent.class)
    void granted() {
        apply(consumers::startUnlessPaused);
    }

    @EventListener(OnRevokedEvent.class)
    void revoked() {
        apply(consumers::stopConsumers);
    }

    @Override
    public void close() {
        roleChanges.shutdown();
    }

    private void apply(Runnable roleChange) {
        if (!roleChanges.isShutdown()) {
            roleChanges.execute(roleChange);
        }
    }
}
