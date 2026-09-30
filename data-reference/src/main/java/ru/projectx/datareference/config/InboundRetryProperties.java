package ru.projectx.datareference.config;

import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Лестница задержек между повторами обработки сырья.
 *
 * <p>В конфигурации, а не константой в коде, ровно потому, что значение может
 * отличаться между dev, stage и prod: на стенде повторы хочется видеть быстро,
 * в проде — не долбить внешнюю систему.
 *
 * @param backoff задержки по попыткам подряд. Попытка, для которой лестница исчерпана,
 *                означает терминальный отказ, а не бесконечный повтор последним шагом:
 *                сообщение, которое не удаётся применить сутки, чинится руками, а не
 *                временем
 */
@Validated
@ConfigurationProperties("projectx.inbound.retry")
public record InboundRetryProperties(
        @DefaultValue({"30s", "5m", "30m"}) @NotEmpty List<Duration> backoff) {

    /**
     * Сколько ждать перед попыткой номер {@code attempt}.
     *
     * @param attempt номер предстоящей попытки, начиная с 1
     * @return задержка либо пусто, если лестница исчерпана
     */
    public Optional<Duration> delayBeforeAttempt(int attempt) {
        int index = attempt - 1;
        if (index < 0 || index >= backoff.size()) {
            return Optional.empty();
        }
        return Optional.of(backoff.get(index));
    }
}
