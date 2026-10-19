package ru.projectx.datareference.config.restread;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.database.rest-read-pool")
public record RestReadPoolProperties(
        @Min(1) int maximumPoolSize,
        @NotNull @DurationMin(millis = 250) Duration connectionTimeout,
        @Min(1) int socketTimeoutSeconds,
        @Min(1) int cancelSignalTimeoutSeconds) {
}
