package ru.projectx.datareference.entity.inbound;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record InboundTransferPart(
        @Nullable UUID correlationId, @Nullable Integer batchCount, @Nullable Integer rowCount) {
}
