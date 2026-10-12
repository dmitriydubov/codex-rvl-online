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

    int size() {
        return node.size();
    }

    String text(String name) {
        return optionalText(name).orElseThrow(() -> absentOrInvalid(name));
    }

    private ContractViolationException absentOrInvalid(String name) {
        JsonNode value = node.path(name);
        return value.isMissingNode() || value.isNull()
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
