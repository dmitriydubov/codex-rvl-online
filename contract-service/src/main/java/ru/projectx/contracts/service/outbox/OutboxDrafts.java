package ru.projectx.contracts.service.outbox;

import java.util.UUID;
import ru.projectx.contracts.entity.MessageEnvelope;
import ru.projectx.contracts.entity.outbox.OutboxDraft;
import ru.projectx.contracts.message.MessageMeta;

public final class OutboxDrafts {

    private OutboxDrafts() {
    }

    public static OutboxDraft of(String aggregateType, UUID aggregateId, MessageMeta meta, String payload) {
        MessageEnvelope envelope = new MessageEnvelope(meta.messageId(), meta.correlationId(), meta.topic(),
                meta.eventType(), meta.entityVersion());
        return new OutboxDraft(aggregateType, aggregateId, envelope, payload);
    }
}
