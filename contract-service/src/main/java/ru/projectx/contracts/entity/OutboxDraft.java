package ru.projectx.contracts.entity;

import java.util.UUID;

public record OutboxDraft(String aggregateType, UUID aggregateId, MessageEnvelope envelope, String payload) {
}
