package ru.projectx.datareference.config;

import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки метрик сервиса.
 *
 * @param backlogRefreshInterval пауза между обновлениями метрик очереди разбора сырых сообщений 1С: столько,
 *                не больше, они отстают от базы. Каждое обновление — четыре подсчёта по частичным индексам, без
 *                выборки строк. Первое обновление — через тот же интервал после старта. Нулевая или отрицательная
 *                пауза не пускает сервис в работу
 */
@Validated
@ConfigurationProperties("projectx.metrics")
public record MetricsProperties(@DefaultValue("30s") @DurationMin(millis = 1) Duration backlogRefreshInterval) {
}
