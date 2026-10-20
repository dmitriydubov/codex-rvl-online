package ru.projectx.datareference.config.cache;

import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.cache.notifications")
public record ReferenceCacheNotificationProperties(
        @DefaultValue("1s") @DurationMin(millis = 1) Duration reconnectDelay,
        @DefaultValue("10s") @DurationMin(millis = 1) Duration reconnectMaxDelay,
        @DefaultValue("10") @Positive long reconnectRetries,
        @DefaultValue("60s") @DurationMin(seconds = 1) Duration silenceCheck) {
}
