package ru.projectx.datareference.service.onec;

import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.mapper.OneCGuid;
import ru.projectx.datareference.mapper.OneCValues;
import tools.jackson.databind.node.ObjectNode;

final class TransferServiceGuid {

    private static final String KEY_VALUE = "keyValue";

    private TransferServiceGuid() {
    }

    static Optional<UUID> readableIn(ObjectNode part) {
        return OneCValues.text(part, KEY_VALUE).flatMap(OneCGuid::parse);
    }

    static UUID requiredIn(ObjectNode part) {
        return OneCValues.requiredUuid(part, KEY_VALUE);
    }

    static Optional<UUID> parse(@Nullable String raw) {
        return Optional.ofNullable(raw).map(String::trim).flatMap(OneCGuid::parse);
    }
}
