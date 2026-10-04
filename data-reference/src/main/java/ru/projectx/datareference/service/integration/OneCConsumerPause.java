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
 * Пауза приёма из 1С:УСК, пока база данных не принимает записи: потребители всех очередей останавливаются,
 * сообщения ждут в очередях брокера, а когда база снова отвечает, потребители возвращаются.
 *
 * <p>Возвращать сообщение брокеру при недоступной базе бессмысленно: оно выдаётся снова первым, снова
 * отказывает, и каждая такая передоставка — одна из ограниченного числа попыток на quorum-очереди, после
 * которых брокер сообщение сбрасывает. Вместо этого потребитель отменяется, как советует документация
 * RabbitMQ для недоступной зависимости: неподтверждённое сообщение возвращается в очередь на прежнее место
 * при закрытии канала контейнером, и порядок потока сохраняется.
 *
 * <p>Останавливаются все очереди сразу, потому что база у них одна. Возвращаются только те контейнеры,
 * которые остановила сама пауза: контейнер, выключенный настройкой автостарта или человеком, она не трогает.
 * Остановка идёт из потока слушателя, поэтому контейнер лишь отменяет потребителя и дожидается конца
 * обработки на своём исполнителе, не блокируя слушатель; запускать контейнер обратно можно только после
 * того, как эта остановка завершилась, иначе он откажет. Повторный сигнал о недоступности во время паузы
 * ничего не меняет: причиной остаётся первый отказ.
 *
 * <p>Пауза и возобновление не пересекаются: пока потребители запускаются, новый отказ ждёт конца запуска и
 * ставит новую паузу — иначе отказ, пришедший с первого же запущенного потребителя, пропал бы вместе с
 * завершающейся паузой, и его сообщение осталось бы без ответа при работающем здоровье.
 *
 * <p>Реестр контейнеров есть только там, где есть брокер; без него (тесты без AMQP) пауза запоминает отказ,
 * а останавливать ей нечего.
 */
@Component
public class OneCConsumerPause {

    private static final Logger log = LoggerFactory.getLogger(OneCConsumerPause.class);

    private final ObjectProvider<RabbitListenerEndpointRegistry> containers;
    private final IngestStorageProbe probe;
    private final Clock clock;
    /** Монитор, под которым меняется состояние паузы: пауза и возобновление не пересекаются. */
    private final Object lifecycle = new Object();
    private volatile @Nullable PauseState state;

    public OneCConsumerPause(
            ObjectProvider<RabbitListenerEndpointRegistry> containers, IngestStorageProbe probe, Clock clock) {

        this.containers = containers;
        this.probe = probe;
        this.clock = clock;
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

    /** Текущая пауза, если потребители остановлены. */
    public Optional<Paused> paused() {
        return Optional.ofNullable(state).map(PauseState::paused);
    }

    /** {@code true}, когда приём не на паузе либо все остановки потребителей уже завершены. */
    public boolean consumersStopped() {
        PauseState current = state;
        return current == null || current.consumersStopped().isDone();
    }

    /**
     * Пауза снимается до запуска: первый же запущенный потребитель может снова отказать и поставить новую
     * паузу, и она не должна пропасть вместе со старой.
     */
    private void resume(PauseState current) {
        if (!current.consumersStopped().isDone()) {
            log.info("база отвечает, но потребители 1С:УСК ещё останавливаются: запуск отложен до следующей проверки");
            return;
        }
        state = null;
        current.paused().queues().forEach(queue -> container(queue).start());
        log.info("база снова отвечает: потребители {} запущены, приём из 1С:УСК возобновлён", current.paused().queues());
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

    /**
     * Пауза приёма: из-за какого отказа, с какого момента и потребители каких очередей остановлены.
     *
     * @param outage отказ хранилища, первым поставивший приём на паузу
     * @param since  момент остановки
     * @param queues имена очередей, потребителей которых остановила пауза
     */
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
