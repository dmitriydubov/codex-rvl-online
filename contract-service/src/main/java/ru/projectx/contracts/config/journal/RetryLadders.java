package ru.projectx.contracts.config.journal;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

final class RetryLadders {

    private RetryLadders() {
    }

    static Optional<Duration> delayAfterFailedAttempt(List<Duration> backoff, int attempt) {
        int index = attempt - 1;
        if (index < 0 || index >= backoff.size()) {
            return Optional.empty();
        }
        return Optional.of(backoff.get(index));
    }
}
