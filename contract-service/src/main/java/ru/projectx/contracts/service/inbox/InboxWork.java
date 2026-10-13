package ru.projectx.contracts.service.inbox;

import java.time.Instant;

@FunctionalInterface
public interface InboxWork {

    void apply(Instant now);
}
