package ru.projectx.datareference.service.metrics;

import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.MetricsProperties;

/**
 * Расписание обновления метрик очереди разбора: раз в заданный интервал снимок перечитывается из базы. Интервал
 * берётся из настроек метрик, уже проверенных на старте. Пока расписания выключены (так делают тесты), Spring этот
 * класс не вызывает, и снимок остаётся пустым, пока его не обновят явно.
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
                metrics::refresh, properties.backlogRefreshInterval(), properties.backlogRefreshInterval()));
    }
}
