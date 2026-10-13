package ru.projectx.contracts.service.outbox;

import java.util.UUID;

public record OutboundMessage(UUID messageId, UUID correlationId, String eventType, String payload) {
}
