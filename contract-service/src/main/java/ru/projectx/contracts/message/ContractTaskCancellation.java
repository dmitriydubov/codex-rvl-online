package ru.projectx.contracts.message;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ContractTaskCancellation(
        UUID individualRequestId,
        Instant cancelledAt,
        String cancelReason,
        @Nullable String cancelComment) {
}
