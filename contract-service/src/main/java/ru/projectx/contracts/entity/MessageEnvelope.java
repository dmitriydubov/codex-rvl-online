package ru.projectx.contracts.entity;

import java.util.UUID;

public record MessageEnvelope(UUID messageId, UUID correlationId, String topic, String eventType, int entityVersion) {
}
