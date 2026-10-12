package ru.projectx.contracts.service.integration;

import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.InboxReceipt;
import ru.projectx.contracts.exception.InboxEnvelopeException;
import ru.projectx.contracts.service.Guids;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TaskEnvelopeParser {

    private static final String META = "meta";
    private static final String MESSAGE_ID = "message_id";
    private static final String CORRELATION_ID = "correlation_id";
    private static final String TOPIC = "topic";
    private static final String EVENT_TYPE = "event_type";
    private static final String ENTITY_VERSION = "entity_version";

    private final JsonMapper json;

    public TaskEnvelopeParser(JsonMapper json) {
        this.json = json;
    }

    public InboxReceipt parse(byte[] body) {
        JsonNode meta = root(body).path(META);
        return new InboxReceipt(uuid(meta, MESSAGE_ID), uuid(meta, CORRELATION_ID), text(meta, TOPIC),
                text(meta, EVENT_TYPE), version(meta));
    }

    private JsonNode root(byte[] body) {
        try {
            return json.readTree(body);
        }
        catch (JacksonException unreadable) {
            throw InboxEnvelopeException.unreadableBody(unreadable);
        }
    }

    private static UUID uuid(JsonNode meta, String field) {
        return Guids.canonical(text(meta, field)).orElseThrow(() -> InboxEnvelopeException.unusableField(field));
    }

    private static String text(JsonNode meta, String field) {
        JsonNode value = meta.path(field);
        if (!value.isString()) {
            throw InboxEnvelopeException.unusableField(field);
        }
        return value.stringValue();
    }

    private static int version(JsonNode meta) {
        JsonNode value = meta.path(ENTITY_VERSION);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw InboxEnvelopeException.unusableField(ENTITY_VERSION);
        }
        return value.intValue();
    }
}
