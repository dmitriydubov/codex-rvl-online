package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class TaskDeliveryTest {

    private final Channel channel = mock();
    private final TaskDelivery delivery = new TaskDelivery(message(), channel);

    @Test
    void delivery_exposesQueueBodyAndPayload() {
        assertThat(delivery.queue()).isEqualTo("task-changes");
        assertThat(delivery.body()).isEqualTo("{\"meta\":\"Тест\"}".getBytes(StandardCharsets.UTF_8));
        assertThat(delivery.payload()).isEqualTo("{\"meta\":\"Тест\"}");
    }

    @Test
    void acknowledge_acksTheDeliveryTagOnly() throws IOException {
        delivery.acknowledge();

        verify(channel).basicAck(42L, false);
    }

    @Test
    void returnToQueue_nacksTheDeliveryTagWithRequeue() throws IOException {
        delivery.returnToQueue();

        verify(channel).basicNack(42L, false, true);
    }

    private static Message message() {
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue("task-changes");
        properties.setDeliveryTag(42L);
        return new Message("{\"meta\":\"Тест\"}".getBytes(StandardCharsets.UTF_8), properties);
    }
}
