package ru.projectx.datareference.acceptance;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.springframework.transaction.support.ResourceHolderSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

final class ReadDeadline {

    private static final Duration LONGEST_WAIT = Duration.ofSeconds(30);

    private ReadDeadline() {
    }

    static void awaitExpiry() {
        Instant deadline = currentDeadline();
        await().atMost(LONGEST_WAIT).until(() -> deadline.isBefore(Instant.now()));
    }

    private static Instant currentDeadline() {
        return TransactionSynchronizationManager.getResourceMap().values().stream()
                .filter(ResourceHolderSupport.class::isInstance)
                .map(ResourceHolderSupport.class::cast)
                .map(ResourceHolderSupport::getDeadline)
                .flatMap(deadline -> Optional.ofNullable(deadline).stream())
                .map(Date::toInstant)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("у текущего чтения нет лимита времени"));
    }
}
