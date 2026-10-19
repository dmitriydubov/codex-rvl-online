package ru.projectx.datareference.entity.inbound;

import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public record InboundOutcome(
        InboundStatus status,
        int attempt,
        @Nullable Instant nextRetryAt,
        @Nullable String lastError,
        @Nullable Instant processedAt,
        @Nullable ParentWait awaited) {

    public Optional<ParentWait> parentWait() {
        return Optional.ofNullable(awaited);
    }
}
