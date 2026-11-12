package ru.projectx.contracts.service.inbox.parse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.service.Guids;
import tools.jackson.databind.JsonNode;

public record JsonField(JsonNode value, String path) {

    public String text() {
        if (!value.isString()) {
            throw absentOrInvalid();
        }
        return value.stringValue();
    }

    public UUID uuid() {
        return Guids.canonical(text()).orElseThrow(() -> ContractViolationException.invalidValue(path));
    }

    public LocalDate date() {
        try {
            return LocalDate.parse(text());
        }
        catch (DateTimeParseException notDate) {
            throw ContractViolationException.invalidValue(path, notDate);
        }
    }

    public int integer() {
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw absentOrInvalid();
        }
        return value.intValue();
    }

    public BigDecimal decimal() {
        if (!value.isNumber()) {
            throw absentOrInvalid();
        }
        return value.decimalValue();
    }

    public boolean bool() {
        if (!value.isBoolean()) {
            throw absentOrInvalid();
        }
        return value.booleanValue();
    }

    public OptionalField optional() {
        return new OptionalField(this);
    }

    boolean isAbsent() {
        return value.isMissingNode() || value.isNull();
    }

    ContractViolationException absentOrInvalid() {
        return isAbsent()
                ? ContractViolationException.missingField(path)
                : ContractViolationException.invalidValue(path);
    }
}
