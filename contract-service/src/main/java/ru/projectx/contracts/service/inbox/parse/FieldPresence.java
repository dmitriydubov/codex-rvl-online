package ru.projectx.contracts.service.inbox.parse;

import org.jspecify.annotations.Nullable;

public enum FieldPresence {
    REQUIRED {
        @Override
        public @Nullable String textOf(JsonFields fields, String name) {
            return fields.field(name).text();
        }

        @Override
        public boolean arrayMissingIn(JsonFields fields, String name) {
            return false;
        }
    },
    OPTIONAL {
        @Override
        public @Nullable String textOf(JsonFields fields, String name) {
            return fields.field(name).optional().text();
        }

        @Override
        public boolean arrayMissingIn(JsonFields fields, String name) {
            return !fields.hasArray(name);
        }
    };

    public abstract @Nullable String textOf(JsonFields fields, String name);

    public abstract boolean arrayMissingIn(JsonFields fields, String name);
}
