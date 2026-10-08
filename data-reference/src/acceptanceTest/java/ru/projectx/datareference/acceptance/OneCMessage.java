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
 * Сообщение 1С:УСК, каким его сохранил бы приём, и статус, которым разбор обязан его закончить: молчаливый пропуск
 * иначе проявился бы только ложным 404 далеко от причины. {@code message-id} выводится из содержимого, если сценарий
 * не задал его явно, чтобы повторный прогон клал те же строки.
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
