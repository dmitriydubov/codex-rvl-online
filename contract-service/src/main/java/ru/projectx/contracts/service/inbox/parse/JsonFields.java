package ru.projectx.contracts.service.inbox.parse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.service.Guids;
import tools.jackson.databind.JsonNode;

public record JsonFields(JsonNode node, String path) {

    JsonFields child(String name) {
        return new JsonFields(node.path(name), pathOf(name));
    }

    public String pathOf(String name) {
        return path.isEmpty() ? name : path + "." + name;
    }

    public JsonFields item(int index) {
        return new JsonFields(node.path(index), path + "[" + index + "]");
    }

    public JsonFields requiredObject(String name) {
        JsonFields child = child(name);
        if (!child.node.isObject()) {
            throw absentOrInvalid(name);
        }
        return child;
    }

    public JsonFields requiredArray(String name) {
        JsonFields child = child(name);
        if (!child.node.isArray()) {
            throw absentOrInvalid(name);
        }
        return child;
    }

    boolean hasArray(String name) {
        JsonNode value = node.path(name);
        if (absent(value)) {
            return false;
        }
        if (!value.isArray()) {
            throw ContractViolationException.invalidValue(pathOf(name));
        }
        return true;
    }

    public int size() {
        return node.size();
    }

    public @Nullable Long longOrNull(String name) {
        JsonNode value = node.path(name);
        if (absent(value)) {
            return null;
        }
        if (notLong(value)) {
            throw ContractViolationException.invalidValue(pathOf(name));
        }
        return value.longValue();
    }

    private static boolean absent(JsonNode value) {
        return value.isMissingNode() || value.isNull();
    }

    private static boolean notLong(JsonNode value) {
        return !value.isIntegralNumber() || !value.canConvertToLong();
    }

    public String text(String name) {
        return optionalText(name).orElseThrow(() -> absentOrInvalid(name));
    }

    private ContractViolationException absentOrInvalid(String name) {
        JsonNode value = node.path(name);
        return absent(value)
                ? ContractViolationException.missingField(pathOf(name))
                : ContractViolationException.invalidValue(pathOf(name));
    }

    Optional<String> optionalText(String name) {
        JsonNode value = node.path(name);
        return value.isString() ? Optional.of(value.stringValue()) : Optional.empty();
    }

    public @Nullable String textOrNull(String name) {
        return optionalText(name).orElse(null);
    }

    public UUID uuid(String name) {
        return Guids.canonical(text(name)).orElseThrow(() -> ContractViolationException.invalidValue(pathOf(name)));
    }

    public @Nullable UUID uuidOrNull(String name) {
        return optionalText(name).isPresent() ? uuid(name) : null;
    }

    public int integer(String name) {
        JsonNode value = node.path(name);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw absentOrInvalid(name);
        }
        return value.intValue();
    }

    public LocalDate date(String name) {
        try {
            return LocalDate.parse(text(name));
        }
        catch (DateTimeParseException notDate) {
            throw ContractViolationException.invalidValue(pathOf(name), notDate);
        }
    }

    public @Nullable LocalDate dateOrNull(String name) {
        return optionalText(name).isPresent() ? date(name) : null;
    }

    public BigDecimal decimal(String name) {
        JsonNode value = node.path(name);
        if (!value.isNumber()) {
            throw absentOrInvalid(name);
        }
        return value.decimalValue();
    }

    public boolean bool(String name) {
        JsonNode value = node.path(name);
        if (!value.isBoolean()) {
            throw absentOrInvalid(name);
        }
        return value.booleanValue();
    }
}
