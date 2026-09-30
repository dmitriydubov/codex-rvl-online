package ru.projectx.datareference.acceptance;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import ru.projectx.datareference.entity.OneCStream;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Сообщение 1С:УСК, каким его сохранил бы приём из брокера: поток, очередь-источник
 * и конверт {@code {data, metadata}} целиком.
 *
 * <p>{@code message-id} выводится из содержимого, а не случаен: повторный прогон сценария
 * кладёт те же строки, и расхождение между прогонами не прячется за разными ключами.
 */
record OneCMessage(OneCStream stream, String queue, ObjectNode data) {

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final String PRODUCER = "1c-usk";

    UUID messageId() {
        return UUID.nameUUIDFromBytes((stream + data.toString()).getBytes(StandardCharsets.UTF_8));
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
