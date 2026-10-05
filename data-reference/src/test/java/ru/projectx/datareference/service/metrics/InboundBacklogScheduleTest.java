package ru.projectx.datareference.service.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import ru.projectx.datareference.config.MetricsProperties;

/** Обновление метрик очереди разбора — одна задача с паузой между проходами из настроек метрик. */
@ExtendWith(MockitoExtension.class)
class InboundBacklogScheduleTest {

    private static final Duration REFRESH_INTERVAL = Duration.ofSeconds(17);

    @Mock
    private InboundBacklogMetrics metrics;

    @Test
    void refresh_isOneFixedDelayTaskWithTheConfiguredInterval() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        new InboundBacklogSchedule(metrics, new MetricsProperties(REFRESH_INTERVAL)).configureTasks(registrar);

        List<IntervalTask> tasks = registrar.getFixedDelayTaskList();
        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().getIntervalDuration()).isEqualTo(REFRESH_INTERVAL);
        assertThat(tasks.getFirst().getInitialDelayDuration()).isEqualTo(REFRESH_INTERVAL);
        assertThat(registrar.getFixedRateTaskList()).isEmpty();
        assertThat(registrar.getCronTaskList()).isEmpty();
        tasks.getFirst().getRunnable().run();
        verify(metrics).refresh();
    }
}
