package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.RejectedMessage;

/**
 * Тело доставки: ведущая метка порядка байтов UTF-8 снимается только в начале и только целиком, сырое тело
 * остаётся как прислал брокер — таким оно уходит в карантин.
 */
@ExtendWith(MockitoExtension.class)
class OneCDeliveryTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";
    private static final UUID MESSAGE_ID = UUID.fromString("9f1c0f2e-0000-4000-8000-00000000000a");
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Mock
    private Channel channel;

    @Test
    void leadingBom_isStrippedFromBodyButKeptInRawBody() {
        byte[] raw = concat(BOM, "{\"a\":1}".getBytes(StandardCharsets.UTF_8));

        OneCDelivery delivery = delivery(raw);

        assertThat(delivery.payload()).isEqualTo("{\"a\":1}");
        assertThat(delivery.rawBody()).isEqualTo(raw);
    }

    @Test
    void bodyWithoutBom_isUnchanged() {
        byte[] raw = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

        assertThat(delivery(raw).body()).isEqualTo(raw);
    }

    /** Метка не в начале — часть содержимого, а не метка: снимать её нельзя. */
    @Test
    void bomInsideBody_isKept() {
        byte[] raw = concat("{".getBytes(StandardCharsets.UTF_8), BOM);

        assertThat(delivery(raw).body()).isEqualTo(raw);
    }

    /** Тело ровно из трёх байт метки — метка без содержимого: конверту достаётся пустое тело, карантину — метка. */
    @Test
    void bodyOfExactlyBom_isEmptiedButKeptInRawBody() {
        OneCDelivery delivery = delivery(BOM.clone());

        assertThat(delivery.body()).isEmpty();
        assertThat(delivery.payload()).isEmpty();
        assertThat(delivery.rawBody()).isEqualTo(BOM);
    }

    /** Два байта метки из трёх — не метка; тело короче метки тоже остаётся как есть. */
    @Test
    void incompleteBom_isKept() {
        byte[] twoBytes = {(byte) 0xEF, (byte) 0xBB};

        assertThat(delivery(twoBytes).body()).isEqualTo(twoBytes);
        assertThat(delivery(new byte[0]).body()).isEmpty();
    }

    @Test
    void rejected_carriesStreamQueueAndRawBody() {
        byte[] raw = concat(BOM, "{}".getBytes(StandardCharsets.UTF_8));

        RejectedMessage rejected = delivery(raw).rejected(MESSAGE_ID);

        assertThat(rejected.messageId()).isEqualTo(MESSAGE_ID);
        assertThat(rejected.stream()).isEqualTo(OneCStream.MKB10);
        assertThat(rejected.sourceQueue()).isEqualTo(QUEUE);
        assertThat(rejected.body()).isEqualTo(raw);
    }

    private OneCDelivery delivery(byte[] raw) {
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue(QUEUE);
        return new OneCDelivery(OneCStream.MKB10, new Message(raw, properties), channel);
    }

    private static byte[] concat(byte[] head, byte[] tail) {
        byte[] joined = new byte[head.length + tail.length];
        System.arraycopy(head, 0, joined, 0, head.length);
        System.arraycopy(tail, 0, joined, head.length, tail.length);
        return joined;
    }
}
