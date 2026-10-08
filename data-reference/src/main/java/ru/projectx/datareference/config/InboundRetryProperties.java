package ru.projectx.datareference.config;

import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Лестница задержек между повторами обработки сырья. Она в конфигурации, потому что на стенде повторы хочется видеть
 * быстро, а в проде нельзя долбить внешнюю систему.
 */
@Validated
@ConfigurationProperties("projectx.inbound.retry")
public record InboundRetryProperties(
        // Когда лестница кончилась, отказ окончательный, а не бесконечный повтор последним шагом: сообщение, которое
        // не удаётся применить сутки, чинят руками, а не временем.
        @DefaultValue({"30s", "5m", "30m"}) @NotEmpty List<Duration> backoff) {

    /** Задержка перед попыткой номер {@code attempt}, считая с 1, или пусто, если лестница исчерпана. */
    public Optional<Duration> delayBeforeAttempt(int attempt) {
        int index = attempt - 1;
        if (index < 0 || index >= backoff.size()) {
            return Optional.empty();
        }
        return Optional.of(backoff.get(index));
    }
}
