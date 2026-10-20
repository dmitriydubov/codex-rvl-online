package ru.projectx.datareference.service.warmup;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;

@Component
public class WarmupRun {

    private static final String TIME_LIMIT = "предел времени";
    private static final String SHUTDOWN = "остановка сервиса";
    private static final String ANSWER = "ответ ";
    private static final String REQUEST_FAILURE = "отказ запроса: ";

    private final Clock clock;

    public WarmupRun(Clock clock) {
        this.clock = clock;
    }

    public WarmupOutcome through(LocalApiClient api, List<String> paths, Instant deadline) {
        int reads = 0;
        for (String path : paths) {
            Optional<String> stop = readBefore(api, path, deadline);
            if (stop.isPresent()) {
                return WarmupOutcome.interrupted(stop.get(), reads);
            }
            reads++;
        }
        return WarmupOutcome.completed(reads);
    }

    private Optional<String> readBefore(LocalApiClient api, String path, Instant deadline) {
        Duration left = Duration.between(clock.instant(), deadline);
        if (!left.isPositive()) {
            return Optional.of(TIME_LIMIT);
        }
        return read(api, path, left);
    }

    private static Optional<String> read(LocalApiClient api, String path, Duration timeout) {
        try {
            return failureOf(api.read(path, timeout));
        } catch (IOException failure) {
            return Optional.of(REQUEST_FAILURE + failure.getClass().getSimpleName());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.of(SHUTDOWN);
        }
    }

    private static Optional<String> failureOf(int status) {
        return Optional.of(status)
                .filter(code -> HttpStatusCode.valueOf(code).is5xxServerError())
                .map(code -> ANSWER + code);
    }
}
