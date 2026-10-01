package ru.projectx.datareference.service.integration;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Одна доставка из очереди 1С: сообщение, канал, по которому брокеру отвечают, и поток,
 * которому очередь принадлежит. Ответы брокеру — только два, и оба здесь, чтобы слушатель
 * говорил «подтвердить» и «вернуть», а не флагами {@code basicNack}.
 */
record OneCDelivery(OneCStream stream, Message message, Channel channel) {

    /** Очередь, из которой пришло сообщение: у прайсов она отличает пакет от финального. */
    String queue() {
        return message.getMessageProperties().getConsumerQueue();
    }

    byte[] body() {
        return message.getBody();
    }

    /** Тело как есть, текстом: в сырьё оно уходит без разбора. */
    String payload() {
        return new String(body(), StandardCharsets.UTF_8);
    }

    /** Сообщение обработано, брокер его больше не отдаёт. */
    void acknowledge() throws IOException {
        channel.basicAck(deliveryTag(), false);
    }

    /** Сообщение не принято, брокер возвращает его в очередь и отдаст снова. */
    void returnToQueue() throws IOException {
        channel.basicNack(deliveryTag(), false, true);
    }

    private long deliveryTag() {
        return message.getMessageProperties().getDeliveryTag();
    }
}
