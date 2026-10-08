package ru.projectx.datareference.service.metrics;

import java.time.Duration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.MetricsProperties;

/**
 * Перечитывает снимок метрик очереди разбора с заданным интервалом. Первое чтение идёт сразу после старта, чтобы
 * окно пустого снимка было коротким. При выключенных расписаниях снимок обновляют только явно.
 */
@Component
public class InboundBacklogSchedule implements SchedulingConfigurer {

    private final InboundBacklogMetrics metrics;
    private final MetricsProperties properties;

    public InboundBacklogSchedule(InboundBacklogMetrics metrics, MetricsProperties properties) {
        this.metrics = metrics;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(new FixedDelayTask(
                metrics::refresh, properties.backlogRefreshInterval(), Duration.ZERO));
    }
}
