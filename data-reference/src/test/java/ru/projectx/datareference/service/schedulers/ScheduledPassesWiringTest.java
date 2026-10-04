package ru.projectx.datareference.service.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.config.SchedulingConfig;
import ru.projectx.datareference.support.ImportSettings;

/**
 * Три прохода по таблице сырья попадают в пул планировщика сервиса так же, как в работающем приложении:
 * настоящие {@code application.yml}, автоконфигурация расписаний Boot, {@link SchedulingConfig} и
 * {@link ScheduledPasses}. Подменены только сами планировщики, базы и брокера нет.
 *
 * <p>Главное утверждение — разбор, повисший на долгом запросе, не останавливает стирание персональных данных
 * и проверку зависших передач. Каждая из них обязана пройти дважды, пока разбор висит.
 */
class ScheduledPassesWiringTest {

    private static final long WAIT_MILLIS = 10_000;

    private final ReferenceImportScheduler importScheduler = mock(ReferenceImportScheduler.class);
    private final PersonalDataRetention retention = mock(PersonalDataRetention.class);
    private final StuckTransferAlert stuckTransferAlert = mock(StuckTransferAlert.class);

    @Test
    void hungImportPass_doesNotStopRetentionAndStuckTransferCheck() {
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> release.await(WAIT_MILLIS, TimeUnit.MILLISECONDS))
                .when(importScheduler).importPendingMessages();

        context().run(started -> {
            try {
                assertThat(started.getBean(ScheduledTaskHolder.class).getScheduledTasks()).hasSize(3);
                verify(importScheduler, timeout(WAIT_MILLIS)).importPendingMessages();
                verify(retention, timeout(WAIT_MILLIS).atLeast(2)).forgetExpiredPayloads();
                verify(stuckTransferAlert, timeout(WAIT_MILLIS).atLeast(2)).reportStuckTransfers();
            }
            finally {
                release.countDown();
            }
        });
    }

    private ApplicationContextRunner context() {
        ImportProperties properties = ImportSettings.defaults()
                .initialDelay(Duration.ZERO)
                .pollInterval(Duration.ofMillis(1))
                .retentionPollInterval(Duration.ofMillis(1))
                .stuckTransferPollInterval(Duration.ofMillis(1))
                .build();
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
                .withUserConfiguration(SchedulingConfig.class)
                .withBean(ScheduledPasses.class, () -> new ScheduledPasses(
                        importScheduler, retention, stuckTransferAlert, properties));
    }
}
