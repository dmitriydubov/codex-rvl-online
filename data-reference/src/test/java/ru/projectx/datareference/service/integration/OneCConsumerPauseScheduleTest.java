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
class OneCConsumerPauseScheduleTest {

    private static final Duration CHECK_INTERVAL = Duration.ofSeconds(13);

    @Mock
    private OneCConsumerPause pause;

    @Test
    void storageCheck_isOneFixedDelayTaskWithTheConfiguredInterval() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        new OneCConsumerPauseSchedule(pause, new IngestProperties(Duration.ofSeconds(5), CHECK_INTERVAL))
                .configureTasks(registrar);

        List<IntervalTask> tasks = registrar.getFixedDelayTaskList();
        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().getIntervalDuration()).isEqualTo(CHECK_INTERVAL);
        assertThat(tasks.getFirst().getInitialDelayDuration()).isEqualTo(CHECK_INTERVAL);
        assertThat(registrar.getFixedRateTaskList()).isEmpty();
        assertThat(registrar.getCronTaskList()).isEmpty();
        tasks.getFirst().getRunnable().run();
        verify(pause).resumeIfStorageAvailable();
    }
}
