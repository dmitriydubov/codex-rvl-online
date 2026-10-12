package ru.projectx.datareference.config;

import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.inbound.retry")
public record InboundRetryProperties(
        @DefaultValue({"30s", "5m", "30m"}) @NotEmpty List<Duration> backoff) {

    public Optional<Duration> delayBeforeAttempt(int attempt) {
        int index = attempt - 1;
        if (index < 0 || index >= backoff.size()) {
            return Optional.empty();
        }
        return Optional.of(backoff.get(index));
    }
}
