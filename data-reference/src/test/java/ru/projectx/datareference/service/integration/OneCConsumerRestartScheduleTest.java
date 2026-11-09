package ru.projectx.datareference.service.integration;

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
import ru.projectx.datareference.config.IngestProperties;

@ExtendWith(MockitoExtension.class)
class OneCConsumerRestartScheduleTest {

    private static final Duration RESTART_INTERVAL = Duration.ofSeconds(17);

    @Mock
    private OneCConsumerRestart restart;

    @Test
    void consumerRestart_isOneFixedDelayTaskWithTheConfiguredInterval() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        IngestProperties properties = new IngestProperties(
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(30), RESTART_INTERVAL);

        new OneCConsumerRestartSchedule(restart, properties).configureTasks(registrar);

        List<IntervalTask> tasks = registrar.getFixedDelayTaskList();
        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().getIntervalDuration()).isEqualTo(RESTART_INTERVAL);
        assertThat(tasks.getFirst().getInitialDelayDuration()).isEqualTo(RESTART_INTERVAL);
        assertThat(registrar.getFixedRateTaskList()).isEmpty();
        assertThat(registrar.getCronTaskList()).isEmpty();
        tasks.getFirst().getRunnable().run();
        verify(restart).restartStopped();
    }
}
