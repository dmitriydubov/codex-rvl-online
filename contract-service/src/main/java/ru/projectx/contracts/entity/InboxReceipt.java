package ru.projectx.contracts.entity;

import java.util.UUID;

public record InboxReceipt(UUID messageId, UUID correlationId, String topic, String eventType, int entityVersion) {
}
