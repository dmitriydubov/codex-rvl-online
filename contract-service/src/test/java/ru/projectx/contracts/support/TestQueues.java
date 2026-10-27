package ru.projectx.contracts.support;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public final class TestQueues {

    private static final boolean DURABLE = true;
    private static final boolean EXCLUSIVE = false;
    private static final boolean AUTO_DELETE = false;

    private final RabbitTemplate broker;

    public TestQueues(ConnectionFactory connections) {
        this.broker = new RabbitTemplate(connections);
    }

    public TestQueues declare(String queue) {
        broker.execute(channel -> channel.queueDeclare(queue, DURABLE, EXCLUSIVE, AUTO_DELETE, null));
        return this;
    }

    public void purge(String queue) {
        broker.execute(channel -> channel.queuePurge(queue));
    }

    public void delete(String queue) {
        broker.execute(channel -> channel.queueDelete(queue));
    }

    public long depth(String queue) {
        return broker.execute(channel -> channel.messageCount(queue));
    }

    public long consumers(String queue) {
        return broker.execute(channel -> channel.consumerCount(queue));
    }
}
