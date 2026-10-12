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
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class OneCConsumerPause {

    private static final Logger log = LoggerFactory.getLogger(OneCConsumerPause.class);

    private final ObjectProvider<RabbitListenerEndpointRegistry> containers;
    private final IngestStorageProbe probe;
    private final Clock clock;
    private final OneCIngestMetrics metrics;
    private final Object lifecycle = new Object();
    private volatile @Nullable PauseState state;

    public OneCConsumerPause(ObjectProvider<RabbitListenerEndpointRegistry> containers, IngestStorageProbe probe,
            Clock clock, OneCIngestMetrics metrics) {

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
            Map<String, CompletableFuture<Void>> stops = runningQueues().stream()
                    .collect(Collectors.toMap(queue -> queue, queue -> new CompletableFuture<>()));
            state = PauseState.of(outage, clock.instant(), stops);
            metrics.paused();
            log.warn("приём из 1С:УСК приостановлен до восстановления базы: {} SQLSTATE {}; останавливаются потребители {}",
                    outage.failureType(), outage.sqlState(), stops.keySet());
            stops.forEach(this::stopConsumer);
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

    private void resume(PauseState current) {
        if (!current.equals(state)) {
            return;
        }
        if (!current.consumersStopped().isDone()) {
            log.info("база отвечает, но потребители 1С:УСК ещё останавливаются: запуск отложен до следующей проверки");
            return;
        }
        state = null;
        current.paused().queues().forEach(this::startConsumer);
        log.info("база снова отвечает: потребители {} запущены, приём из 1С:УСК возобновлён", current.paused().queues());
    }

    private void startConsumer(String queue) {
        try {
            container(queue).start();
        }
        catch (RuntimeException notStarted) {
            log.error("потребитель очереди {} не запущен после паузы: {}", queue, notStarted.getClass().getName());
        }
    }

    private void stopConsumer(String queue, CompletableFuture<Void> stopped) {
        try {
            container(queue).stop(() -> stopped.complete(null));
        }
        catch (RuntimeException notStopped) {
            log.warn("потребитель очереди {} не остановлен: {}", queue, notStopped.getClass().getName());
            stopped.complete(null);
        }
    }

    private Set<String> runningQueues() {
        return Optional.ofNullable(containers.getIfAvailable())
                .map(RabbitListenerEndpointRegistry::getListenerContainerIds)
                .orElse(Set.of())
                .stream()
                .filter(queue -> container(queue).isRunning())
                .collect(Collectors.toSet());
    }

    private MessageListenerContainer container(String queue) {
        return Optional.ofNullable(containers.getObject().getListenerContainer(queue))
                .orElseThrow(() -> new IllegalStateException("нет контейнера слушателя очереди " + queue));
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
