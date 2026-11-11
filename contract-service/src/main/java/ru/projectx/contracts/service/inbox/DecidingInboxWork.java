package ru.projectx.contracts.service.inbox;

import java.time.Instant;
import java.util.Optional;

@FunctionalInterface
public interface DecidingInboxWork extends InboxWork {

    @Override
    Optional<String> applyOrSkip(Instant now);

    @Override
    default void apply(Instant now) {
        applyOrSkip(now);
    }
}
