package ru.projectx.contracts.config.payment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.payment-orders")
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public record PaymentOrderPassesProperties(
        @NotNull @DurationMin(millis = 1) Duration creationInterval,
        @NotNull @DurationMin(millis = 1) Duration cancellationInterval,
        @Min(1) int batchSize,
        @NotNull @DurationMin(millis = 1) Duration awaitingInterval,
        @NotNull @DurationMin(millis = 1) Duration awaitingThreshold) {
}
