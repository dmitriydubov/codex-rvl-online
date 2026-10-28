package ru.projectx.contracts.service.inbox.parse;

import org.springframework.stereotype.Component;
import ru.projectx.contracts.exception.ContractViolationException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

@Component
public class MessageBodies {

    public static final String META = "meta";
    public static final String DATA = "data";

    private static final String ROOT = "";

    private final ObjectReader json;

    public MessageBodies(JsonMapper json) {
        this.json = json.reader(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    public JsonFields data(String payload) {
        return body(payload).requiredObject(DATA);
    }

    public JsonFields body(String payload) {
        try {
            return new JsonFields(json.readTree(payload), ROOT);
        }
        catch (JacksonException unreadable) {
            throw ContractViolationException.unreadableBody(unreadable);
        }
    }
}
