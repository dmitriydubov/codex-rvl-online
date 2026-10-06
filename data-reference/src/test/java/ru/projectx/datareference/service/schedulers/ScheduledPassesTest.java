package ru.projectx.datareference.service.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.support.ImportSettings;

/**
 * Три прохода по таблице сырья регистрируются с паузами и начальной задержкой из настроек.
 *
 * <p>Регистратор задач — настоящий, Spring здесь не поднимается: что регистратор делает с задачей
 * дальше, решает Spring, а проверяется то, что ему передано. У каждой паузы своё значение, поэтому
 * перепутанные между проходами настройки видны. Какой проход стоит за задачей, узнаётся её запуском.
 */
@ExtendWith(MockitoExtension.class)
class ScheduledPassesTest {

    private static final Duration INITIAL_DELAY = Duration.ofMinutes(7);
    private static final Duration IMPORT_PAUSE = Duration.ofSeconds(11);
    private static final Duration RETENTION_PAUSE = Duration.ofHours(2);
    private static final Duration STUCK_TRANSFER_PAUSE = Duration.ofMinutes(16);

    @Mock
    private ReferenceImportScheduler importScheduler;

    @Mock
    private PersonalDataRetention retention;

    @Mock
    private StuckTransferAlert stuckTransferAlert;

    @Test
    void importPass_runsWithItsPauseAfterTheInitialDelay() {
        IntervalTask task = registeredTask(0);

        assertThat(task.getIntervalDuration()).isEqualTo(IMPORT_PAUSE);
        assertThat(task.getInitialDelayDuration()).isEqualTo(INITIAL_DELAY);
        task.getRunnable().run();
        verify(importScheduler).importPendingMessages();
        verifyNoInteractions(retention, stuckTransferAlert);
    }

    @Test
    void retentionPass_runsWithItsPauseAfterTheInitialDelay() {
        IntervalTask task = registeredTask(1);

        assertThat(task.getIntervalDuration()).isEqualTo(RETENTION_PAUSE);
        assertThat(task.getInitialDelayDuration()).isEqualTo(INITIAL_DELAY);
        task.getRunnable().run();
        verify(retention).forgetExpiredPersonalData();
        verifyNoInteractions(importScheduler, stuckTransferAlert);
    }

    @Test
    void stuckTransferCheck_runsWithItsPauseAfterTheInitialDelay() {
        IntervalTask task = registeredTask(2);

        assertThat(task.getIntervalDuration()).isEqualTo(STUCK_TRANSFER_PAUSE);
        assertThat(task.getInitialDelayDuration()).isEqualTo(INITIAL_DELAY);
        task.getRunnable().run();
        verify(stuckTransferAlert).reportStuckTransfers();
        verifyNoInteractions(importScheduler, retention);
    }

    /**
     * Задачи — с паузой между проходами, а не с постоянной частотой: долгий проход разбора не должен
     * накладываться на следующий, иначе одно сообщение разбирали бы два прохода сразу.
     */
    @Test
    void allThreePasses_areFixedDelayTasksOnly() {
        ScheduledTaskRegistrar registrar = configured();

        assertThat(registrar.getFixedDelayTaskList()).hasSize(3);
        assertThat(registrar.getFixedRateTaskList()).isEmpty();
        assertThat(registrar.getCronTaskList()).isEmpty();
        assertThat(registrar.getTriggerTaskList()).isEmpty();
    }

    private IntervalTask registeredTask(int index) {
        List<IntervalTask> tasks = configured().getFixedDelayTaskList();
        assertThat(tasks).hasSize(3);
        return tasks.get(index);
    }

    private ScheduledTaskRegistrar configured() {
        ImportProperties properties = ImportSettings.defaults()
                .initialDelay(INITIAL_DELAY)
                .pollInterval(IMPORT_PAUSE)
                .retentionPollInterval(RETENTION_PAUSE)
                .stuckTransferPollInterval(STUCK_TRANSFER_PAUSE)
                .build();
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        new ScheduledPasses(importScheduler, retention, stuckTransferAlert, properties).configureTasks(registrar);
        return registrar;
    }
}
