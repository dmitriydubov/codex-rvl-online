package ru.projectx.contracts.service.inbox;

import java.time.Instant;
import java.util.Optional;

@FunctionalInterface
public interface InboxWork {

    void apply(Instant now);

    default Optional<String> applyOrSkip(Instant now) {
        apply(now);
        return Optional.empty();
    }
}
