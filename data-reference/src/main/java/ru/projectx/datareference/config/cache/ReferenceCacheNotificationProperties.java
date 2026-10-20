package ru.projectx.datareference.config.cache;

import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.cache.notifications")
public record ReferenceCacheNotificationProperties(
        @DefaultValue("1s") @DurationMin(millis = 1) Duration reconnectDelay,
        @DefaultValue("10s") @DurationMin(millis = 1) Duration reconnectMaxDelay) {

    @AssertTrue(message = "наибольшая пауза между попытками подключения не может быть короче первой")
    public boolean isMaxDelayNotShorterThanDelay() {
        return reconnectMaxDelay.compareTo(reconnectDelay) >= 0;
    }
}
