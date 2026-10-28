package ru.projectx.datareference.service.integration;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.IngestProperties;

@Component
class OneCQueueContainers {

    private static final Logger log = LoggerFactory.getLogger(OneCQueueContainers.class);

    private final ObjectProvider<RabbitListenerEndpointRegistry> registry;
    private final OneCConsumerExpectation expectation;
    private final OneCConsumerOutageJournal journal;
    private final IngestProperties properties;
    private final Map<String, CompletableFuture<Void>> stopping = new ConcurrentHashMap<>();

    OneCQueueContainers(ObjectProvider<RabbitListenerEndpointRegistry> registry, OneCConsumerExpectation expectation,
            OneCConsumerOutageJournal journal, IngestProperties properties) {

        this.registry = registry;
        this.expectation = expectation;
        this.journal = journal;
        this.properties = properties;
    }

    void startExpected() {
        for (String queue : expectedQueues()) {
            start(queue);
        }
    }

    Set<String> stopped() {
        return expectedQueues().stream()
                .filter(queue -> !container(queue).isRunning() && !stopInProgress(queue))
                .collect(Collectors.toSet());
    }

    void start(String queue) {
        try {
            container(queue).start();
        }
        catch (RuntimeException notStarted) {
            journal.startFailed(queue, notStarted.getClass().getName());
        }
    }

    void awaitStops() {
        CompletableFuture.allOf(stopping.values().toArray(CompletableFuture[]::new))
                .completeOnTimeout(null, properties.consumerStopTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .join();
        stopping.values().removeIf(CompletableFuture::isDone);
        if (!stopping.isEmpty()) {
            log.warn("потребители очередей {} не остановились за {}: их запуск может не пройти",
                    stopping.keySet(), properties.consumerStopTimeout());
        }
    }

    void stopRunning() {
        for (String queue : running()) {
            stop(queue, () -> { });
        }
    }

    Set<String> running() {
        return queues().stream()
                .filter(queue -> container(queue).isRunning())
                .collect(Collectors.toSet());
    }

    void stop(String queue, Runnable stopped) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        stopping.put(queue, done);
        Runnable finished = () -> {
            done.complete(null);
            stopped.run();
        };
        try {
            container(queue).stop(finished);
        }
        catch (RuntimeException notStopped) {
            log.warn("потребитель очереди {} не остановлен: {}", queue, notStopped.getClass().getName());
            finished.run();
        }
    }

    private Set<String> expectedQueues() {
        return expectation.consumersExpected() ? queues() : Set.of();
    }

    private boolean stopInProgress(String queue) {
        return Optional.ofNullable(stopping.get(queue)).filter(stop -> !stop.isDone()).isPresent();
    }

    private Set<String> queues() {
        return Optional.ofNullable(registry.getIfAvailable())
                .map(RabbitListenerEndpointRegistry::getListenerContainerIds)
                .orElse(Set.of());
    }

    private MessageListenerContainer container(String queue) {
        return Optional.ofNullable(registry.getObject().getListenerContainer(queue))
                .orElseThrow(() -> new IllegalStateException("нет контейнера слушателя очереди " + queue));
    }
}
