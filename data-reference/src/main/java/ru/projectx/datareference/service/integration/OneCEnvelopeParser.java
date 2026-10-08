package ru.projectx.datareference.service.integration;

import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.exception.OneCEnvelopeException;
import ru.projectx.datareference.mapper.OneCGuid;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Достаёт из конверта сообщения 1С:УСК {@code message-id}, ключ дедупликации и строки сырья; остальное разбирается
 * позже из сохранённого тела. Нулевой GUID в 1С означает пустое значение, поэтому нулевой {@code message-id} считается
 * отсутствующим.
 */
@Component
public class OneCEnvelopeParser {

    private static final String METADATA = "metadata";
    private static final String MESSAGE_ID = "message-id";
    private static final String NO_MESSAGE_ID = "в metadata нет обязательного message-id";

    private final ObjectMapper json;

    public OneCEnvelopeParser(ObjectMapper json) {
        this.json = json;
    }

    /**
     * @throws OneCEnvelopeException если тело не JSON, а {@code message-id} отсутствует, нулевой или записан не как
     * GUID в канонической форме
     */
    public UUID messageIdOf(byte[] body) {
        JsonNode envelope = readTree(body);
        JsonNode metadata = metadataOf(envelope);
        return requireNotEmpty(parse(messageIdText(metadata)));
    }

    private JsonNode readTree(byte[] body) {
        try {
            return json.readTree(body);
        }
        catch (JacksonException malformed) {
            throw new OneCEnvelopeException("тело сообщения не является читаемым JSON", malformed);
        }
    }

    private static JsonNode metadataOf(JsonNode envelope) {
        JsonNode metadata = envelope.path(METADATA);
        if (!metadata.isObject()) {
            throw new OneCEnvelopeException("в конверте нет объекта metadata");
        }
        return metadata;
    }

    private static String messageIdText(JsonNode metadata) {
        String raw = metadata.path(MESSAGE_ID).asString(null);
        if (raw == null || raw.isBlank()) {
            throw new OneCEnvelopeException(NO_MESSAGE_ID);
        }
        return raw;
    }

    /**
     * Только каноническая запись GUID: нестрогий разбор прочитал бы искажённый {@code message-id} как чужой ключ
     * дедупликации. Значения нет в тексте исключения, потому что текст уходит в журнал, а этим путём идут полисы.
     */
    private static UUID parse(String raw) {
        return OneCGuid.parse(raw)
                .orElseThrow(() -> new OneCEnvelopeException("metadata.message-id не является GUID"));
    }

    /**
     * Нулевой {@code message-id} отклоняется так же, как отсутствующий. Иначе первое такое сообщение
     * легло бы в сырьё под нулевым ключом, а все следующие считались бы его дублями и терялись,
     * оставляя в журнале только DEBUG о дубле.
     */
    private static UUID requireNotEmpty(UUID messageId) {
        if (OneCGuid.isEmpty(messageId)) {
            throw new OneCEnvelopeException(NO_MESSAGE_ID);
        }
        return messageId;
    }
}
