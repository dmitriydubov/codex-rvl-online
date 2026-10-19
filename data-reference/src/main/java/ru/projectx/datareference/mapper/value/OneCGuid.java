package ru.projectx.datareference.mapper.value;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class OneCGuid {

    private static final Pattern CANONICAL = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static final UUID EMPTY = new UUID(0L, 0L);

    public static Optional<UUID> parse(String raw) {
        return Optional.of(raw)
                .filter(text -> CANONICAL.matcher(text).matches())
                .map(UUID::fromString);
    }

    public static boolean isEmpty(UUID guid) {
        return EMPTY.equals(guid);
    }

    private OneCGuid() {
    }
}
