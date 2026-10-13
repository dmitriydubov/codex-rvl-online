package ru.projectx.contracts.service.inbox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.service.Guids;
import tools.jackson.databind.JsonNode;

record JsonFields(JsonNode node, String path) {

    JsonFields child(String name) {
        return new JsonFields(node.path(name), pathOf(name));
    }

    String pathOf(String name) {
        return path.isEmpty() ? name : path + "." + name;
    }

    JsonFields item(int index) {
        return new JsonFields(node.path(index), path + "[" + index + "]");
    }

    JsonFields requiredObject(String name) {
        JsonFields child = child(name);
        if (!child.node.isObject()) {
            throw absentOrInvalid(name);
        }
        return child;
    }

    JsonFields requiredArray(String name) {
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

    int size() {
        return node.size();
    }

    @Nullable Long longOrNull(String name) {
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

    String text(String name) {
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

    @Nullable String textOrNull(String name) {
        return optionalText(name).orElse(null);
    }

    UUID uuid(String name) {
        return Guids.canonical(text(name)).orElseThrow(() -> ContractViolationException.invalidValue(pathOf(name)));
    }

    @Nullable UUID uuidOrNull(String name) {
        return optionalText(name).isPresent() ? uuid(name) : null;
    }

    int integer(String name) {
        JsonNode value = node.path(name);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw absentOrInvalid(name);
        }
        return value.intValue();
    }

    LocalDate date(String name) {
        try {
            return LocalDate.parse(text(name));
        }
        catch (DateTimeParseException notDate) {
            throw ContractViolationException.invalidValue(pathOf(name), notDate);
        }
    }

    @Nullable LocalDate dateOrNull(String name) {
        return optionalText(name).isPresent() ? date(name) : null;
    }

    BigDecimal decimal(String name) {
        JsonNode value = node.path(name);
        if (!value.isNumber()) {
            throw absentOrInvalid(name);
        }
        return value.decimalValue();
    }

    boolean bool(String name) {
        JsonNode value = node.path(name);
        if (!value.isBoolean()) {
            throw absentOrInvalid(name);
        }
        return value.booleanValue();
    }
}
