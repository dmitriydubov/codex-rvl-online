package ru.projectx.contracts.service.integration;

import ru.projectx.contracts.entity.InboxReceipt;

public sealed interface TaskEnvelope {

    InboxReceipt receipt();

    record Storable(InboxReceipt receipt) implements TaskEnvelope {
    }

    record Unstorable(InboxReceipt receipt) implements TaskEnvelope {
    }
}
