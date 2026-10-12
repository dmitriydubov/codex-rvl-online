package ru.projectx.contracts.service;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class Guids {

    private static final Pattern CANONICAL =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private Guids() {
    }

    public static Optional<UUID> canonical(String value) {
        return CANONICAL.matcher(value).matches() ? Optional.of(UUID.fromString(value)) : Optional.empty();
    }
}
