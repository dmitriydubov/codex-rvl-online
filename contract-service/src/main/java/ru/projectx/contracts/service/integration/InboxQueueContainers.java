package ru.projectx.contracts.service.integration;

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
import ru.projectx.contracts.config.InboxProperties;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;

@Component
class InboxQueueContainers {

    private static final Logger log = LoggerFactory.getLogger(InboxQueueContainers.class);

    private final ObjectProvider<RabbitListenerEndpointRegistry> registry;
    private final InboxQueues queues;
    private final BackgroundLeadership leadership;
    private final InboxProperties.Pause pause;
    private final Map<String, CompletableFuture<Void>> stopping = new ConcurrentHashMap<>();

    InboxQueueContainers(ObjectProvider<RabbitListenerEndpointRegistry> registry, InboxQueues queues,
            BackgroundLeadership leadership, InboxProperties properties) {
        this.registry = registry;
        this.queues = queues;
        this.leadership = leadership;
        this.pause = properties.pause();
    }

    boolean consumersExpected() {
        return leadership.leader() && !queues.consumed().isEmpty();
    }

    void startExpected() {
        if (!leadership.leader()) {
            return;
        }
        for (String queue : queues.consumed()) {
            start(queue);
        }
    }

    void awaitStops() {
        CompletableFuture.allOf(stopping.values().toArray(CompletableFuture[]::new))
                .completeOnTimeout(null, pause.consumerStopTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .join();
        stopping.values().removeIf(CompletableFuture::isDone);
        if (!stopping.isEmpty()) {
            log.warn("потребители очередей {} не остановились за {}: их запуск может не пройти",
                    stopping.keySet(), pause.consumerStopTimeout());
        }
    }

    void stopRunning() {
        for (String queue : running()) {
            stop(queue, () -> { });
        }
    }

    Set<String> running() {
        return registered().stream()
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

    private void start(String queue) {
        try {
            container(queue).start();
        }
        catch (RuntimeException notStarted) {
            log.error("потребитель очереди {} не запущен: {}", queue, notStarted.getClass().getName());
        }
    }

    private Set<String> registered() {
        return Optional.ofNullable(registry.getIfAvailable())
                .map(RabbitListenerEndpointRegistry::getListenerContainerIds)
                .orElse(Set.of());
    }

    private MessageListenerContainer container(String queue) {
        return Optional.ofNullable(registry.getObject().getListenerContainer(queue))
                .orElseThrow(() -> new IllegalStateException("нет контейнера слушателя очереди " + queue));
    }
}
