package ru.projectx.datareference.acceptance;

import java.util.Collection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

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

    public void declare(String queue) {
        broker.execute(channel -> channel.queueDeclare(queue, DURABLE, EXCLUSIVE, AUTO_DELETE, null));
    }

    public void delete(String queue) {
        broker.execute(channel -> channel.queueDelete(queue));
    }
}
