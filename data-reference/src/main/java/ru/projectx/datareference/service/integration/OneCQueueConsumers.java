package ru.projectx.datareference.service.integration;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.OneCQueueProperties;

/**
 * Очереди 1С:УСК, которые по настройкам должен читать сервис, а брокер подписку их потребителя не подтвердил. Где
 * слушатели выключены или брокера нет, перечень пуст. Он упорядочен по имени, чтобы ответ ручки здоровья не менялся от
 * запроса к запросу.
 */
@Component
public class OneCQueueConsumers {

    private final OneCQueueProperties queues;
    private final ObjectProvider<RabbitListenerEndpointRegistry> containers;
    private final OneCConsumerSubscriptions subscriptions;

    public OneCQueueConsumers(OneCQueueProperties queues, ObjectProvider<RabbitListenerEndpointRegistry> containers,
            OneCConsumerSubscriptions subscriptions) {

        this.queues = queues;
        this.containers = containers;
        this.subscriptions = subscriptions;
    }

    /** Очереди, у которых должен быть потребитель, а брокер его подписку не подтвердил. */
    public List<String> queuesWithoutConsumer() {
        return Optional.ofNullable(containers.getIfAvailable())
                .map(this::queuesWithoutConsumer)
                .orElse(List.of());
    }

    private List<String> queuesWithoutConsumer(RabbitListenerEndpointRegistry registry) {
        return Arrays.stream(OneCQueue.values())
                .map(queue -> queue.nameIn(queues))
                .filter(queue -> startedBySettings(registry.getListenerContainer(queue)))
                .filter(queue -> !subscriptions.subscribed(queue))
                .sorted()
                .toList();
    }

    private static boolean startedBySettings(MessageListenerContainer container) {
        return container != null && container.isAutoStartup();
    }
}
