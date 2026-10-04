package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Расписания сервиса: включены ли они и хватает ли им потоков.
 *
 * <p>Контекст собирается из настоящего {@code application.yml} и автоконфигурации расписаний Boot, без базы
 * и брокера. Файл настроек важен: в нём включены виртуальные потоки, и с ними Boot по умолчанию поставил бы
 * планировщик, который выполняет все задачи с паузой между проходами на одном общем потоке. Тогда
 * разбор сообщений, повисший на чужой блокировке, остановил бы и стирание персональных данных, и проверку
 * зависших передач.
 */
class SchedulingConfigTest {

    private static final long WAIT_SECONDS = 10;

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
            .withUserConfiguration(SchedulingConfig.class);

    @Test
    void schedulingPool_hasAThreadForEachOfTheThreeSchedulers() {
        context.run(started -> assertThat(started.getBean(ThreadPoolTaskScheduler.class)
                .getScheduledThreadPoolExecutor().getCorePoolSize()).isGreaterThanOrEqualTo(3));
    }

    /**
     * Одна задача зависла — две другие продолжают проходить. Каждая из двух обязана пройти дважды: первый
     * проход мог случиться до того, как зависшая заняла поток, второй — уже нет.
     */
    @Test
    void hungTask_doesNotStopTheOtherTwo() {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch otherPasses = new CountDownLatch(4);
        SchedulingConfigurer threeTasks = registrar -> {
            registrar.addFixedDelayTask(() -> awaitQuietly(release), Duration.ofMillis(1));
            registrar.addFixedDelayTask(otherPasses::countDown, Duration.ofMillis(1));
            registrar.addFixedDelayTask(otherPasses::countDown, Duration.ofMillis(1));
        };

        context.withBean(SchedulingConfigurer.class, () -> threeTasks).run(started -> {
            try {
                assertThat(otherPasses.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            }
            finally {
                release.countDown();
            }
        });
    }

    @Test
    void schedulesAreOnByDefault() {
        context.withBean(SchedulingConfigurer.class, () -> SchedulingConfigTest::addIdleTask)
                .run(started -> assertThat(started.getBean(ScheduledTaskHolder.class).getScheduledTasks()).hasSize(1));
    }

    /** Выключенные расписания — ни одной задачи и ни одного потока планировщика: тесты зовут проходы сами. */
    @Test
    void switchedOffSchedules_registerNoTaskAndNoPool() {
        context.withPropertyValues("projectx.scheduling.enabled=false")
                .withBean(SchedulingConfigurer.class, () -> SchedulingConfigTest::addIdleTask)
                .run(started -> {
                    assertThat(started).doesNotHaveBean(ScheduledTaskHolder.class);
                    assertThat(started).doesNotHaveBean(ThreadPoolTaskScheduler.class);
                });
    }

    private static void addIdleTask(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(() -> { }, Duration.ofHours(1));
    }

    private static void awaitQuietly(CountDownLatch release) {
        try {
            release.await(WAIT_SECONDS, TimeUnit.SECONDS);
        }
        catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }
}
