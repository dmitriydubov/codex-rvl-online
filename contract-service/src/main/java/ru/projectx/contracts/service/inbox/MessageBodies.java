package ru.projectx.contracts.service.inbox;

import org.springframework.stereotype.Component;
import ru.projectx.contracts.exception.ContractViolationException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

@Component
public class MessageBodies {

    private static final String ROOT = "";
    private static final String DATA = "data";

    private final ObjectReader json;

    public MessageBodies(JsonMapper json) {
        this.json = json.reader(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    JsonFields data(String payload) {
        try {
            return new JsonFields(json.readTree(payload), ROOT).requiredObject(DATA);
        }
        catch (JacksonException unreadable) {
            throw ContractViolationException.unreadableBody(unreadable);
        }
    }
}
