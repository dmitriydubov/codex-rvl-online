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

/**
 * Пауза приёма из 1С:УСК, пока база не принимает записи: потребители всех очередей останавливаются, сообщения ждут в
 * брокере на своих местах, и брокеру их не возвращают, чтобы не тратить ограниченные передоставки quorum-очереди. Когда
 * база отвечает, пауза запускает только те контейнеры, которые остановила сама.
 */
@Component
public class OneCConsumerPause {

    private static final Logger log = LoggerFactory.getLogger(OneCConsumerPause.class);

    private final ObjectProvider<RabbitListenerEndpointRegistry> containers;
    private final IngestStorageProbe probe;
    private final Clock clock;
    private final OneCIngestMetrics metrics;
    /** Монитор, под которым меняется состояние паузы: пауза и возобновление не пересекаются. */
    private final Object lifecycle = new Object();
    private volatile @Nullable PauseState state;

    public OneCConsumerPause(ObjectProvider<RabbitListenerEndpointRegistry> containers, IngestStorageProbe probe,
            Clock clock, OneCIngestMetrics metrics) {

        this.containers = containers;
        this.probe = probe;
        this.clock = clock;
        this.metrics = metrics;
    }

    /** Останавливает работающих потребителей очередей 1С:УСК; во время паузы ничего не делает. */
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

    /** Проверяет базу и, если она снова принимает записи приёма, возвращает остановленных потребителей. */
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

    /** {@code true}, когда приём не на паузе либо все остановки потребителей уже завершены. */
    public boolean consumersStopped() {
        PauseState current = state;
        return current == null || current.consumersStopped().isDone();
    }

    /**
     * Вызывается под монитором {@code lifecycle}: отказ с первого запущенного потребителя ждёт на нём конца запуска и
     * ставит новую паузу, а не пропадает вместе со старой. Если пауза сменилась после проверки базы, ею займётся
     * следующая проверка.
     */
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

    /**
     * Отказ запуска по причине брокера, например пропавшая очередь, не задерживает остальные очереди: контейнер
     * остаётся остановленным, и ручка здоровья называет очередь без потребителя.
     */
    private void startConsumer(String queue) {
        try {
            container(queue).start();
        }
        catch (RuntimeException notStarted) {
            log.error("потребитель очереди {} не запущен после паузы: {}", queue, notStarted.getClass().getName());
        }
    }

    /**
     * Отказ остановки одного контейнера не должен заклинить возобновление всех: его остановка считается
     * завершённой, а сам он, если и остался работать, при запуске просто не изменится.
     */
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

    /** Пауза приёма: первый отказ хранилища, момент остановки и очереди, потребителей которых остановила пауза. */
    public record Paused(StorageOutage outage, Instant since, Set<String> queues) {
    }

    /** Пауза вместе с признаком, что все остановки контейнеров завершились. */
    private record PauseState(Paused paused, CompletableFuture<Void> consumersStopped) {

        static PauseState of(StorageOutage outage, Instant since, Map<String, CompletableFuture<Void>> stops) {
            return new PauseState(
                    new Paused(outage, since, Set.copyOf(stops.keySet())),
                    CompletableFuture.allOf(stops.values().toArray(CompletableFuture[]::new)));
        }
    }
}
