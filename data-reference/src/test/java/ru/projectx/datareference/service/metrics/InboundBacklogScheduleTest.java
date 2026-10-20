package ru.projectx.datareference.service.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
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
import ru.projectx.datareference.support.TestLeadership;

@ExtendWith(MockitoExtension.class)
class InboundBacklogScheduleTest {

    private static final Duration REFRESH_INTERVAL = Duration.ofSeconds(17);

    @Mock
    private InboundBacklogMetrics metrics;

    @Test
    void leader_refreshesTheSnapshot() {
        new InboundBacklogSchedule(metrics, new MetricsProperties(REFRESH_INTERVAL), TestLeadership.leader())
                .refreshOnSchedule();

        verify(metrics).refresh();
        verify(metrics, never()).withdraw();
    }

    @Test
    void standby_withdrawsTheSnapshotInsteadOfReadingTheDatabase() {
        new InboundBacklogSchedule(metrics, new MetricsProperties(REFRESH_INTERVAL), TestLeadership.standby())
                .refreshOnSchedule();

        verify(metrics).withdraw();
        verify(metrics, never()).refresh();
    }

    @Test
    void refresh_isOneFixedDelayTaskWithTheConfiguredInterval() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        new InboundBacklogSchedule(metrics, new MetricsProperties(REFRESH_INTERVAL), TestLeadership.leader()).configureTasks(registrar);

        List<IntervalTask> tasks = registrar.getFixedDelayTaskList();
        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().getIntervalDuration()).isEqualTo(REFRESH_INTERVAL);
        assertThat(tasks.getFirst().getInitialDelayDuration()).isZero();
        assertThat(registrar.getFixedRateTaskList()).isEmpty();
        assertThat(registrar.getCronTaskList()).isEmpty();
        tasks.getFirst().getRunnable().run();
        verify(metrics).refresh();
    }
}
