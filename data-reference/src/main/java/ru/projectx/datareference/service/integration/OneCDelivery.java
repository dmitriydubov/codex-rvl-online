package ru.projectx.datareference.service.integration;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.RejectedMessage;

/**
 * Одна доставка из очереди 1С: сообщение, канал для ответа брокеру и поток очереди. Ведущая метка порядка байтов UTF-8
 * (BOM) снимается: колонка {@code jsonb} её не принимает, а дефектом тела она не является, это след редактора.
 */
record OneCDelivery(OneCStream stream, Message message, Channel channel) {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** Очередь, из которой пришло сообщение: у прайсов она отличает пакет от финального. */
    String queue() {
        return message.getMessageProperties().getConsumerQueue();
    }

    /** Тело без ведущей метки порядка байтов: его разбирает конверт и его сохраняет сырьё. */
    byte[] body() {
        byte[] raw = rawBody();
        return startsWithBom(raw) ? Arrays.copyOfRange(raw, UTF8_BOM.length, raw.length) : raw;
    }

    /** Тело как прислал брокер, байт в байт: таким оно уходит в карантин. */
    byte[] rawBody() {
        return message.getBody();
    }

    /** Тело как есть, текстом: в сырьё оно уходит без разбора. */
    String payload() {
        return new String(body(), StandardCharsets.UTF_8);
    }

    /** Это сообщение как отвергнутое базой: для карантина, с телом байт в байт. */
    RejectedMessage rejected(UUID messageId) {
        return new RejectedMessage(messageId, stream, queue(), rawBody());
    }

    /** Сообщение обработано, брокер его больше не отдаёт. */
    void acknowledge() throws IOException {
        channel.basicAck(deliveryTag(), false);
    }

    /** Сообщение не принято, брокер возвращает его в очередь и отдаст снова. */
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
