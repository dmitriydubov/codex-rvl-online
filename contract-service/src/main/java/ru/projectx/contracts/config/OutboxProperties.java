package ru.projectx.contracts.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.outbox")
public record OutboxProperties(
        @Valid @NotNull Publishing publishing,
        @NotNull @DurationMin(millis = 1) Duration confirmTimeout,
        @Valid @NotNull Retry retry,
        @Valid @NotNull Retention retention) {

    public record Retention(
            @NotNull @DurationMin(millis = 1) Duration payload,
            @NotNull @DurationMin(millis = 1) Duration interval,
            @Min(1) int batchSize) {
    }

    public record Retry(@NotEmpty List<@NotNull @DurationMin(millis = 0) Duration> backoff) {

        public Optional<Duration> delayAfterFailedAttempt(int attempt) {
            return RetryLadders.delayAfterFailedAttempt(backoff, attempt);
        }
    }

    public record Publishing(@NotNull @DurationMin(millis = 1) Duration interval, @Min(1) int batchSize) {
    }
}
