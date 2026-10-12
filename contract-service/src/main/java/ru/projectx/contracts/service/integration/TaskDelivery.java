package ru.projectx.contracts.service.integration;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;

record TaskDelivery(Message message, Channel channel) {

    String queue() {
        return message.getMessageProperties().getConsumerQueue();
    }

    byte[] body() {
        return message.getBody();
    }

    String payload() {
        return new String(body(), StandardCharsets.UTF_8);
    }

    void acknowledge() throws IOException {
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    void returnToQueue() throws IOException {
        channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
    }
}
