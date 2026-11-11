package ru.projectx.contracts.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import ru.projectx.contracts.ContractServiceApplication;
import ru.projectx.contracts.config.journal.InboxProperties;
import ru.projectx.contracts.service.integration.consumer.InboxConsumerPause;
import ru.projectx.contracts.service.integration.consumer.InboxConsumerPauseSchedule;

class SchedulerPoolSizeTest {

    private static final String CLASS_FILE = ".class";

    private static List<Class<?>> serviceClasses;

    @BeforeAll
    static void loadServiceClasses() throws IOException, URISyntaxException {
        Path root = Path.of(ContractServiceApplication.class.getProtectionDomain().getCodeSource().getLocation()
                .toURI());
        assertThat(root).isDirectory();
        try (Stream<Path> files = Files.walk(root)) {
            serviceClasses = files.filter(file -> file.toString().endsWith(CLASS_FILE))
                    .<Class<?>>map(file -> load(root.relativize(file)))
                    .toList();
        }
    }

    @Test
    void schedulerPool_hasAThreadForEveryScheduledTaskOfTheServiceWithPaymentOn() throws IOException {
        int scheduledTasks = annotatedTasks() + tasksRegisteredByThePauseSchedule();

        assertThat(poolSize()).isGreaterThanOrEqualTo(scheduledTasks);
    }

    @Test
    void scheduledMethods_areFoundInThePassesOfTheServiceIncludingPayment() {
        assertThat(serviceClasses.stream().filter(type -> scheduledMethods(type) > 0).map(Class::getSimpleName))
                .contains("InboxScheduler", "OutboxScheduler", "PayloadRetentionScheduler", "PaymentOrderScheduler",
                        "PaymentOrderCancellingScheduler", "PaymentInvoiceScheduler");
        assertThat(annotatedTasks()).isPositive();
    }

    @Test
    void consumerPauseSchedule_isTheOnlyTaskRegisteredOutsideScheduledMethods() {
        assertThat(serviceClasses.stream().filter(SchedulingConfigurer.class::isAssignableFrom))
                .containsExactly(InboxConsumerPauseSchedule.class);
        assertThat(tasksRegisteredByThePauseSchedule()).isEqualTo(1);
    }

    private static int annotatedTasks() {
        return serviceClasses.stream().mapToInt(SchedulerPoolSizeTest::scheduledMethods).sum();
    }

    private static int scheduledMethods(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .mapToInt(method -> AnnotatedElementUtils
                        .getMergedRepeatableAnnotations(method, Scheduled.class, Schedules.class).size())
                .sum();
    }

    private static int tasksRegisteredByThePauseSchedule() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        new InboxConsumerPauseSchedule(mock(InboxConsumerPause.class), inboxProperties()).configureTasks(registrar);
        return registrar.getFixedDelayTaskList().size() + registrar.getFixedRateTaskList().size()
                + registrar.getCronTaskList().size() + registrar.getTriggerTaskList().size();
    }

    private static InboxProperties inboxProperties() {
        return new InboxProperties(new InboxProperties.Processing(Duration.ofSeconds(10), 100),
                new InboxProperties.Retry(List.of(Duration.ofMinutes(1))),
                new InboxProperties.Retention(Duration.ofDays(30), Duration.ofHours(1), 500),
                new InboxProperties.Pause(Duration.ofSeconds(10), Duration.ofSeconds(30)), Duration.ofSeconds(5));
    }

    private static int poolSize() throws IOException {
        Object size = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .getFirst().getProperty("spring.task.scheduling.pool.size");
        return Integer.parseInt(String.valueOf(size));
    }

    private static Class<?> load(Path classFile) {
        String path = classFile.toString();
        String name = path.substring(0, path.length() - CLASS_FILE.length())
                .replace(classFile.getFileSystem().getSeparator(), ".");
        try {
            return Class.forName(name, false, SchedulerPoolSizeTest.class.getClassLoader());
        }
        catch (ClassNotFoundException missing) {
            throw new IllegalStateException("класс сервиса не загружен: " + name, missing);
        }
    }
}
