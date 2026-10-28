package ru.projectx.contracts.service.inbox.parse;

import org.jspecify.annotations.Nullable;

public enum FieldPresence {
    REQUIRED {
        @Override
        public @Nullable String textOf(JsonFields fields, String name) {
            return fields.text(name);
        }

        @Override
        public boolean arrayMissingIn(JsonFields fields, String name) {
            return false;
        }
    },
    OPTIONAL {
        @Override
        public @Nullable String textOf(JsonFields fields, String name) {
            return fields.textOrNull(name);
        }

        @Override
        public boolean arrayMissingIn(JsonFields fields, String name) {
            return !fields.hasArray(name);
        }
    };

    public abstract @Nullable String textOf(JsonFields fields, String name);

    public abstract boolean arrayMissingIn(JsonFields fields, String name);
}
