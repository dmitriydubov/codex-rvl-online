package ru.projectx.contracts.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Клиент сервиса справочных данных: адрес его внутреннего API, таймауты и повторы. Таймаут чтения держится не меньше
 * семи секунд: при отказе своей базы справочник отвечает 503 примерно за пять, и клиент не должен обрывать запрос
 * раньше этого ответа.
 */
@Validated
@ConfigurationProperties("projectx.reference")
public record ReferenceProperties(
        @NotBlank @Pattern(regexp = "https?://.+") String url,
        @NotNull @DurationMin(millis = 1) Duration connectTimeout,
        @NotNull @DurationMin(millis = 1) Duration readTimeout,
        @Valid @NotNull Retry retry) {

    /** Повторы одного чтения: сколько раз повторить после первой неудачи и сколько ждать между попытками. */
    public record Retry(@Min(0) int maxRetries, @NotNull @DurationMin(millis = 0) Duration delay) {
    }
}
