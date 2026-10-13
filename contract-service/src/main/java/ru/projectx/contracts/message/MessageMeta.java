package ru.projectx.contracts.message;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MessageMeta(
        UUID messageId,
        UUID correlationId,
        String topic,
        String eventType,
        String schemaVersion,
        OffsetDateTime createdAt,
        int entityVersion,
        String source) {
}
