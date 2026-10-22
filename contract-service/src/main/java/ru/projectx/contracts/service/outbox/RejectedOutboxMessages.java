package ru.projectx.contracts.service.outbox;

import java.time.Instant;
import java.util.UUID;

public interface RejectedOutboxMessages {

    String aggregateType();

    void rejected(UUID aggregateId, Instant now);
}
