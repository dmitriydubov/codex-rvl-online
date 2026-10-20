package ru.projectx.datareference.service.metrics;

import java.time.Duration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.MetricsProperties;
import ru.projectx.datareference.service.leadership.BackgroundLeadership;

@Component
public class InboundBacklogSchedule implements SchedulingConfigurer {

    private final InboundBacklogMetrics metrics;
    private final MetricsProperties properties;
    private final BackgroundLeadership leadership;

    public InboundBacklogSchedule(InboundBacklogMetrics metrics, MetricsProperties properties,
            BackgroundLeadership leadership) {

        this.metrics = metrics;
        this.properties = properties;
        this.leadership = leadership;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(new FixedDelayTask(this::refreshOnSchedule, properties.backlogRefreshInterval(),
                Duration.ZERO));
    }

    public void refreshOnSchedule() {
        leadership.runByRole(metrics::refresh, metrics::withdraw);
    }
}
