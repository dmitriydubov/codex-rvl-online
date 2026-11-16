package ru.projectx.contracts.service.inbox.parse;

import java.util.function.Predicate;
import ru.projectx.contracts.exception.ContractViolationException;
import tools.jackson.databind.JsonNode;

public record JsonFields(JsonNode node, String path) {

    public JsonField field(String name) {
        return new JsonField(node.path(name), path.isEmpty() ? name : path + "." + name);
    }

    public JsonFields requiredObject(String name) {
        return requiredStructure(name, JsonNode::isObject);
    }

    public JsonFields requiredArray(String name) {
        return requiredStructure(name, JsonNode::isArray);
    }

    boolean hasArray(String name) {
        JsonField field = field(name);
        if (field.isAbsent()) {
            return false;
        }
        if (!field.value().isArray()) {
            throw ContractViolationException.invalidValue(field.path());
        }
        return true;
    }

    public JsonFields item(int index) {
        return new JsonFields(node.path(index), path + "[" + index + "]");
    }

    public int size() {
        return node.size();
    }

    private JsonFields requiredStructure(String name, Predicate<JsonNode> kind) {
        JsonField field = field(name);
        if (!kind.test(field.value())) {
            throw field.absentOrInvalid();
        }
        return new JsonFields(field.value(), field.path());
    }
}
