package ru.projectx.datareference.service.integration;

import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.exception.OneCEnvelopeException;
import ru.projectx.datareference.mapper.value.OneCGuid;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OneCEnvelopeParser {

    private static final String METADATA = "metadata";
    private static final String MESSAGE_ID = "message-id";
    private static final String NO_MESSAGE_ID = "в metadata нет обязательного message-id";

    private final ObjectMapper json;

    public OneCEnvelopeParser(ObjectMapper json) {
        this.json = json;
    }

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

    private static UUID parse(String raw) {
        return OneCGuid.parse(raw)
                .orElseThrow(() -> new OneCEnvelopeException("metadata.message-id не является GUID"));
    }

    private static UUID requireNotEmpty(UUID messageId) {
        if (OneCGuid.isEmpty(messageId)) {
            throw new OneCEnvelopeException(NO_MESSAGE_ID);
        }
        return messageId;
    }
}
