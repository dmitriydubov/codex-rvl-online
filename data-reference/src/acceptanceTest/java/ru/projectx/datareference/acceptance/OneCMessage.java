package ru.projectx.datareference.acceptance;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Сообщение 1С:УСК, каким его сохранил бы приём из брокера: поток, очередь-источник
 * и конверт {@code {data, metadata}} целиком.
 *
 * <p>{@code message-id} выводится из содержимого, а не случаен: повторный прогон сценария
 * кладёт те же строки, и расхождение между прогонами не прячется за разными ключами. Сценарий, которому
 * важен сам идентификатор (повтор с тем же или другим {@code message-id}), задаёт его явно.
 *
 * <p>{@code expected} — каким статусом штатный разбор обязан закончить это сообщение.
 * Пропуск ({@code SKIPPED}) — тоже исход, но у каждого сообщения свой: санаторий или
 * договор, молча ушедшие в пропуск (другой вид деятельности, другой тип продукта),
 * иначе проявились бы только ложным 404 далеко от причины.
 *
 * @param expected       статус строки сырья после разбора
 * @param fixedMessageId {@code message-id}, заданный сценарием, или пусто — тогда он выводится из содержимого
 */
record OneCMessage(
        OneCStream stream, String queue, ObjectNode data, InboundStatus expected, @Nullable UUID fixedMessageId) {

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final String PRODUCER = "1c-usk";

    OneCMessage(OneCStream stream, String queue, ObjectNode data, InboundStatus expected) {
        this(stream, queue, data, expected, null);
    }

    UUID messageId() {
        return Optional.ofNullable(fixedMessageId)
                .orElseGet(() -> UUID.nameUUIDFromBytes((stream + data.toString()).getBytes(StandardCharsets.UTF_8)));
    }

    String envelope() {
        ObjectNode envelope = JSON.createObjectNode();
        envelope.set("data", data);
        envelope.putObject("metadata")
                .put("message-id", messageId().toString())
                .put("topic", "")
                .put("producer", PRODUCER);
        return envelope.toString();
    }
}
