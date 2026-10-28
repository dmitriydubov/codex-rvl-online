package ru.projectx.contracts.entity.outbox;

import java.util.UUID;
import ru.projectx.contracts.entity.MessageEnvelope;

public record OutboxDraft(String aggregateType, UUID aggregateId, MessageEnvelope envelope, String payload) {
}
