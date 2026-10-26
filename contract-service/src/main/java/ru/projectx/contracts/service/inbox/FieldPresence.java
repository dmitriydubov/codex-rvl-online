package ru.projectx.contracts.service.inbox;

import org.jspecify.annotations.Nullable;

public enum FieldPresence {
    REQUIRED {
        @Override
        @Nullable String textOf(JsonFields fields, String name) {
            return fields.text(name);
        }

        @Override
        boolean arrayMissingIn(JsonFields fields, String name) {
            return false;
        }
    },
    OPTIONAL {
        @Override
        @Nullable String textOf(JsonFields fields, String name) {
            return fields.textOrNull(name);
        }

        @Override
        boolean arrayMissingIn(JsonFields fields, String name) {
            return !fields.hasArray(name);
        }
    };

    abstract @Nullable String textOf(JsonFields fields, String name);

    abstract boolean arrayMissingIn(JsonFields fields, String name);
}
