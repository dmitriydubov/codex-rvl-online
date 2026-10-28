package ru.projectx.contracts.config.usk;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.lm-policy-requests")
public record LmPolicyRequestProperties(
        @NotNull @DurationMin(millis = 1) Duration interval,
        @Min(1) int batchSize,
        @NotNull @DurationMin(millis = 1) Duration awaitingInterval,
        @NotNull @DurationMin(seconds = 1) Duration awaitingThreshold) {
}
