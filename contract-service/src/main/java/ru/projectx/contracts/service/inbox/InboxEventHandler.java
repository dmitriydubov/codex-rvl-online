package ru.projectx.contracts.service.inbox;

import java.time.Instant;

public interface InboxEventHandler {

    String eventType();

    void handle(String payload, Instant now);
}
