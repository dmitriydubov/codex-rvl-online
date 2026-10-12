package ru.projectx.datareference.service;

import java.util.Optional;
import java.util.UUID;
import ru.projectx.datareference.mapper.OneCGuid;

public final class CanonicalGuids {

    public static Optional<UUID> parse(String raw) {
        return OneCGuid.parse(raw);
    }

    private CanonicalGuids() {
    }
}
