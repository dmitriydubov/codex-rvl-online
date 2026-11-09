package ru.projectx.datareference.service.integration;

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
public class OneCConsumerPause {

    private static final Logger log = LoggerFactory.getLogger(OneCConsumerPause.class);

    private final OneCQueueContainers containers;
    private final IngestStorageProbe probe;
    private final Clock clock;
    private final OneCIngestMetrics metrics;
    private final Object lifecycle = new Object();
    private volatile @Nullable PauseState state;

    OneCConsumerPause(OneCQueueContainers containers, IngestStorageProbe probe, Clock clock, OneCIngestMetrics metrics) {
        this.containers = containers;
        this.probe = probe;
        this.clock = clock;
        this.metrics = metrics;
    }

    public void pause(StorageOutage outage) {
        synchronized (lifecycle) {
            if (state != null) {
                log.debug("приём из 1С:УСК уже на паузе, повторный отказ хранилища: SQLSTATE {}", outage.sqlState());
                return;
            }
            Map<String, CompletableFuture<Void>> stops = containers.running().stream()
                    .collect(Collectors.toMap(queue -> queue, queue -> new CompletableFuture<>()));
            state = PauseState.of(outage, clock.instant(), stops);
            metrics.paused();
            log.warn("приём из 1С:УСК приостановлен до восстановления базы: {} SQLSTATE {}; останавливаются потребители {}",
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
            log.info("база по-прежнему недоступна, приём из 1С:УСК остаётся на паузе с {}", current.paused().since());
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
        unlessPaused(containers::startExpected);
    }

    void unlessPaused(Runnable consumersChange) {
        synchronized (lifecycle) {
            if (state == null) {
                consumersChange.run();
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
            log.info("база отвечает, но потребители 1С:УСК ещё останавливаются: запуск отложен до следующей проверки");
            return;
        }
        state = null;
        containers.startExpected();
        log.info("база снова отвечает: пауза приёма из 1С:УСК с {} снята, потребители запущены, если экземпляр ведущий",
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
