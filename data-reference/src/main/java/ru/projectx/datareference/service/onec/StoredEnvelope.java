package ru.projectx.datareference.service.onec;

import java.util.Optional;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

final class StoredEnvelope {

    private static final String DATA = "data";

    static final int MAX_STORED_NUMBER_DIGITS = 131_072 + 16_383;

    private final ObjectMapper json;
    private final TokenStreamFactory storedJson;

    StoredEnvelope(ObjectMapper json) {
        this.json = json;
        this.storedJson = json.tokenStreamFactory().rebuild()
                .streamReadConstraints(json.tokenStreamFactory().streamReadConstraints().rebuild()
                        .maxNumberLength(MAX_STORED_NUMBER_DIGITS)
                        .build())
                .build();
    }

    Optional<ObjectNode> dataOf(InboundMessage message) {
        return Optional.ofNullable(message.payload()).map(this::dataOf);
    }

    private ObjectNode dataOf(String payload) {
        return Optional.ofNullable(readTree(payload))
                .map(envelope -> envelope.get(DATA))
                .filter(JsonNode::isObject)
                .map(ObjectNode.class::cast)
                .orElseThrow(() -> new OneCFieldException("в конверте сообщения нет объекта data"));
    }

    private JsonNode readTree(String payload) {
        try (JsonParser parser = storedJson.createParser(ObjectReadContext.empty(), payload)) {
            return json.readTree(parser);
        }
        catch (JacksonException malformed) {
            throw new OneCFieldException("тело сообщения не читается как JSON", malformed);
        }
    }
}
