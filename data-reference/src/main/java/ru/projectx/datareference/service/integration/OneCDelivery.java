package ru.projectx.datareference.service.integration;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.RejectedMessage;

record OneCDelivery(OneCStream stream, Message message, Channel channel) {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    String queue() {
        return message.getMessageProperties().getConsumerQueue();
    }

    byte[] body() {
        byte[] raw = rawBody();
        return startsWithBom(raw) ? Arrays.copyOfRange(raw, UTF8_BOM.length, raw.length) : raw;
    }

    byte[] rawBody() {
        return message.getBody();
    }

    String payload() {
        return new String(body(), StandardCharsets.UTF_8);
    }

    RejectedMessage rejected(UUID messageId) {
        return new RejectedMessage(messageId, stream, queue(), rawBody());
    }

    void acknowledge() throws IOException {
        channel.basicAck(deliveryTag(), false);
    }

    void returnToQueue() throws IOException {
        channel.basicNack(deliveryTag(), false, true);
    }

    private static boolean startsWithBom(byte[] raw) {
        return raw.length >= UTF8_BOM.length && Arrays.equals(raw, 0, UTF8_BOM.length, UTF8_BOM, 0, UTF8_BOM.length);
    }

    private long deliveryTag() {
        return message.getMessageProperties().getDeliveryTag();
    }
}
