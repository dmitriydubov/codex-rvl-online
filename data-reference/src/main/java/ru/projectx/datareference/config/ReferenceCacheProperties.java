package ru.projectx.datareference.config;

import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.cache")
public record ReferenceCacheProperties(
        @DefaultValue("10m") @DurationMin(millis = 1) Duration ttl,
        @DefaultValue("1000") @Positive long maximumSize) {
}
