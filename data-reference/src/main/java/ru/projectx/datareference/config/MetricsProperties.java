package ru.projectx.datareference.config;

import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Настройки метрик сервиса. */
@Validated
@ConfigurationProperties("projectx.metrics")
public record MetricsProperties(
        // Как часто обновляются метрики очереди разбора сырых сообщений: на столько они могут отставать от базы.
        // Обновление — четыре подсчёта по частичным индексам без выборки строк, первое идёт сразу после старта.
        @DefaultValue("30s") @DurationMin(millis = 1) Duration backlogRefreshInterval) {
}
