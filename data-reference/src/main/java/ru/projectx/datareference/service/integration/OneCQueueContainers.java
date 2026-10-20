package ru.projectx.datareference.service.integration;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.service.leadership.BackgroundLeadership;

@Component
class OneCQueueContainers {

    private static final Logger log = LoggerFactory.getLogger(OneCQueueContainers.class);

    private final ObjectProvider<RabbitListenerEndpointRegistry> registry;
    private final ObjectProvider<RabbitProperties> rabbit;
    private final BackgroundLeadership leadership;
    private final Map<String, CompletableFuture<Void>> stopping = new ConcurrentHashMap<>();

    OneCQueueContainers(ObjectProvider<RabbitListenerEndpointRegistry> registry, ObjectProvider<RabbitProperties> rabbit,
            BackgroundLeadership leadership) {

        this.registry = registry;
        this.rabbit = rabbit;
        this.leadership = leadership;
    }

    boolean consumersExpected() {
        return leadership.leader() && consumersEnabled();
    }

    void startExpected() {
        if (!consumersExpected()) {
            return;
        }
        for (String queue : queues()) {
            start(queue);
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

    private void start(String queue) {
        try {
            Optional.ofNullable(stopping.remove(queue)).ifPresent(CompletableFuture::join);
            container(queue).start();
        }
        catch (RuntimeException notStarted) {
            log.error("потребитель очереди {} не запущен: {}", queue, notStarted.getClass().getName());
        }
    }

    private boolean consumersEnabled() {
        return Optional.ofNullable(rabbit.getIfAvailable())
                .map(properties -> properties.getListener().getSimple().isAutoStartup())
                .orElse(false);
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
