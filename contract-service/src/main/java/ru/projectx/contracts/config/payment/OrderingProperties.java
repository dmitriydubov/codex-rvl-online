package ru.projectx.contracts.config.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.time.ZoneId;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("projectx.ordering")
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public record OrderingProperties(
        @NotBlank @Pattern(regexp = HTTP_URL) String url,
        @NotNull @DurationMin(millis = 1) Duration connectTimeout,
        @NotNull @DurationMin(millis = 1) Duration readTimeout,
        @NotBlank String product,
        @NotBlank String channel,
        @NotBlank @DefaultValue(DEFAULT_CANCEL_REASON) String cancelReason,
        @Valid @NotNull Retry retry,
        @Valid @NotNull Document document,
        @Valid @NotNull Token token) {

    static final String HTTP_URL = "https?://.+[^/]";
    static final String DEFAULT_CANCEL_REASON = "notActual";

    public record Retry(@Min(1) int attempts, @NotNull @DurationMin(millis = 0) Duration delay) {
    }

    public record Document(@NotNull ZoneId zone, @NotNull @DurationMin(millis = 0) Duration ttlBeforeCheckIn) {
    }

    public record Token(
            @NotBlank @Pattern(regexp = HTTP_URL) String uri,
            @NotBlank String clientId,
            @NotBlank String clientSecret,
            @NotNull @DurationMin(millis = 1) Duration connectTimeout,
            @NotNull @DurationMin(millis = 1) Duration readTimeout) {

        @Override
        public String toString() {
            return "Token[uri=" + uri + ", clientId=" + clientId + "]";
        }
    }
}
