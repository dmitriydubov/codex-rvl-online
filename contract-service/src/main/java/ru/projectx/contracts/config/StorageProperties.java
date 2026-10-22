package ru.projectx.contracts.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.storage")
public record StorageProperties(
        @NotBlank @Pattern(regexp = "https?://.+[^/]") String endpoint,
        @NotBlank String region,
        @NotBlank String bucket,
        @NotBlank @Pattern(regexp = "[^/].*[^/]|[^/]") String basePath,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @Nullable String sslBundle,
        @Valid @NotNull Timeouts timeouts) {

    public record Timeouts(
            @NotNull @DurationMin(millis = 1) Duration connect,
            @NotNull @DurationMin(millis = 1) Duration read,
            @NotNull @DurationMin(millis = 1) Duration write,
            @NotNull @DurationMin(millis = 1) Duration call) {
    }
}
