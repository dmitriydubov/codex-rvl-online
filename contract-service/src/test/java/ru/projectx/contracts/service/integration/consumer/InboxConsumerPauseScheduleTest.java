package ru.projectx.contracts.service.integration.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

class InboxConsumerPauseScheduleTest {

    private final InboxConsumerPause pause = mock();
    private final ScheduledTaskRegistrar registrar = mock();

    @Test
    void storageCheck_runsWithTheConfiguredIntervalAndStartsAfterIt() {
        new InboxConsumerPauseSchedule(pause, QueueContainersFixture.propertiesStoppingWithin(Duration.ofSeconds(30)))
                .configureTasks(registrar);

        ArgumentCaptor<FixedDelayTask> task = ArgumentCaptor.captor();
        verify(registrar).addFixedDelayTask(task.capture());
        assertThat(task.getValue().getIntervalDuration()).isEqualTo(Duration.ofSeconds(10));
        assertThat(task.getValue().getInitialDelayDuration()).isEqualTo(Duration.ofSeconds(10));
        task.getValue().getRunnable().run();
        verify(pause).resumeIfStorageAvailable();
    }
}
