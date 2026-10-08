package ru.projectx.datareference.acceptance;

import java.util.Collection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Очереди 1С:УСК на брокере стенда: их объявляет и удаляет стенд, как на контуре сторона 1С:УСК. Объявление идёт прямо
 * в канал, а не бинами: увидев бины очередей, потребитель сервиса сам объявил бы пропавшую очередь заново, а на
 * контуре пропавшая очередь остаётся пропавшей.
 */
public class OneCStandQueues {

    private static final boolean DURABLE = true;
    private static final boolean EXCLUSIVE = false;
    private static final boolean AUTO_DELETE = false;

    private final RabbitTemplate broker;

    OneCStandQueues(ConnectionFactory connections) {
        this.broker = new RabbitTemplate(connections);
    }

    void declareAll(Collection<String> queues) {
        queues.forEach(this::declare);
    }

    /** Объявляет очередь с теми же свойствами, что у очередей контура: долговечную и общую. */
    public void declare(String queue) {
        broker.execute(channel -> channel.queueDeclare(queue, DURABLE, EXCLUSIVE, AUTO_DELETE, null));
    }

    /** Удаляет очередь вместе с сообщениями; брокер отменяет подписки её потребителей. */
    public void delete(String queue) {
        broker.execute(channel -> channel.queueDelete(queue));
    }
}
