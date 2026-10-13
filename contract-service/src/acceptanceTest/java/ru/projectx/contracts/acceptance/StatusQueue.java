package ru.projectx.contracts.acceptance;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public class StatusQueue {

    private static final boolean DURABLE = true;
    private static final boolean EXCLUSIVE = false;
    private static final boolean AUTO_DELETE = false;
    private static final boolean AUTO_ACK = true;
    private static final String ALIAS = "состояние выпуска";

    private final RabbitTemplate broker;
    private final String queue;

    StatusQueue(ConnectionFactory connections, String queue) {
        this.broker = new RabbitTemplate(connections);
        this.queue = queue;
        declare();
    }

    StatusQueue called(String alias) {
        if (!ALIAS.equals(alias)) {
            throw new IllegalArgumentException("стенд знает только очередь «" + ALIAS + "», а не «" + alias + "»");
        }
        return this;
    }

    String name() {
        return queue;
    }

    void declare() {
        broker.execute(channel -> channel.queueDeclare(queue, DURABLE, EXCLUSIVE, AUTO_DELETE, null));
    }

    void delete() {
        broker.execute(channel -> channel.queueDelete(queue));
    }

    void purge() {
        broker.execute(channel -> channel.queuePurge(queue));
    }

    AMQP.Queue.DeclareOk state() {
        return broker.execute(channel -> channel.queueDeclarePassive(queue));
    }

    List<GetResponse> drain() {
        return broker.execute(channel -> {
            List<GetResponse> received = new ArrayList<>();
            for (GetResponse next = channel.basicGet(queue, AUTO_ACK); next != null; next = channel.basicGet(queue, AUTO_ACK)) {
                received.add(next);
            }
            return received;
        });
    }
}
