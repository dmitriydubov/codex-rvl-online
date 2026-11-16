package ru.projectx.contracts.service.integration.listener;

import ru.projectx.contracts.entity.inbox.InboxReceipt;
import ru.projectx.contracts.exception.InboxEnvelopeException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class EnvelopeBodies {

    private EnvelopeBodies() {
    }

    static JsonNode read(JsonMapper json, byte[] body) {
        try {
            return json.readTree(body);
        }
        catch (JacksonException unreadable) {
            throw InboxEnvelopeException.unreadableBody(unreadable);
        }
    }

    static TaskEnvelope envelope(JsonNode body, InboxReceipt receipt) {
        return UnstorableText.present(body) ? new TaskEnvelope.Unstorable(receipt) : new TaskEnvelope.Storable(receipt);
    }
}
