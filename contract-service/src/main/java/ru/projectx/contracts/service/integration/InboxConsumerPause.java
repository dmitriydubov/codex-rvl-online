package ru.projectx.contracts.service.integration;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InboxConsumerPause {

    private static final Logger log = LoggerFactory.getLogger(InboxConsumerPause.class);

    private final InboxQueueContainers containers;
    private final InboxStorageProbe probe;
    private final Clock clock;
    private final Object lifecycle = new Object();
    private volatile @Nullable PauseState state;

    InboxConsumerPause(InboxQueueContainers containers, InboxStorageProbe probe, Clock clock) {
        this.containers = containers;
        this.probe = probe;
        this.clock = clock;
    }

    public void pause(StorageOutage outage) {
        synchronized (lifecycle) {
            if (state != null) {
                log.debug("приём уже на паузе, повторный отказ базы: SQLSTATE {}", outage.sqlState());
                return;
            }
            Map<String, CompletableFuture<Void>> stops = containers.running().stream()
                    .collect(Collectors.toMap(queue -> queue, queue -> new CompletableFuture<>()));
            state = PauseState.of(outage, clock.instant(), stops);
            log.warn("приём из очередей приостановлен до восстановления базы: {} SQLSTATE {}; останавливаются потребители {}",
                    outage.failureType(), outage.sqlState(), stops.keySet());
            stops.forEach((queue, stopped) -> containers.stop(queue, () -> stopped.complete(null)));
        }
    }

    public void resumeIfStorageAvailable() {
        PauseState current = state;
        if (current == null) {
            return;
        }
        if (!probe.storageAvailable()) {
            log.info("база по-прежнему недоступна, приём из очередей остаётся на паузе с {}", current.paused().since());
            return;
        }
        synchronized (lifecycle) {
            resume(current);
        }
    }

    public Optional<Paused> paused() {
        return Optional.ofNullable(state).map(PauseState::paused);
    }

    public boolean consumersStopped() {
        PauseState current = state;
        return current == null || current.consumersStopped().isDone();
    }

    void startUnlessPaused() {
        containers.awaitStops();
        synchronized (lifecycle) {
            if (state == null) {
                containers.startExpected();
            }
        }
    }

    void stopConsumers() {
        Optional<StorageOutage> outage = probe.outage();
        synchronized (lifecycle) {
            outage.ifPresentOrElse(this::pause, containers::stopRunning);
        }
    }

    void stopConsumersWithoutProbe() {
        synchronized (lifecycle) {
            containers.stopRunning();
        }
    }

    private void resume(PauseState current) {
        if (!current.equals(state)) {
            return;
        }
        if (!current.consumersStopped().isDone()) {
            log.info("база отвечает, но потребители очередей ещё останавливаются: запуск отложен до следующей проверки");
            return;
        }
        state = null;
        containers.startExpected();
        log.info("база снова отвечает: пауза приёма с {} снята, потребители запущены, если экземпляр ведущий",
                current.paused().since());
    }

    public record Paused(StorageOutage outage, Instant since, Set<String> queues) {
    }

    private record PauseState(Paused paused, CompletableFuture<Void> consumersStopped) {

        static PauseState of(StorageOutage outage, Instant since, Map<String, CompletableFuture<Void>> stops) {
            return new PauseState(
                    new Paused(outage, since, Set.copyOf(stops.keySet())),
                    CompletableFuture.allOf(stops.values().toArray(CompletableFuture[]::new)));
        }
    }
}
