package ru.projectx.datareference.service.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.service.onec.ReferenceImportService;

/**
 * Первый тик каждого из трёх планировщиков сырья — не раньше задержки
 * {@code projectx.import.initial-delay}.
 *
 * <p>Расписание разбирает настоящий Spring: контекст без Boot, базы и брокера, в котором
 * {@code @EnableScheduling} читает аннотации планировщиков и подставляет плейсхолдеры
 * из окружения. Подменён только исполнитель: вместо потоков — заглушка
 * {@link TaskScheduler} с остановленными часами, которой остаётся запомнить, на какой момент
 * ей поручили первый запуск. По контракту
 * {@link TaskScheduler#scheduleWithFixedDelay(Runnable, Instant, Duration)} это и есть желаемый
 * момент первого выполнения, а последний аргумент — пауза между окончанием прохода и началом
 * следующего. Ждать настоящего тика тест не может: это был бы тест, который ждёт минуты.
 * Заглушка подключается через {@link SchedulingConfigurer}: момент первого запуска Spring
 * считает по часам того исполнителя, что стоит в регистраторе задач.
 *
 * <p>Задачи различаются по периоду: у трёх планировщиков он свой, и заодно проверяется, что
 * периоды остались прежними.
 */
class SchedulersFirstTickTest {

    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");
    private static final Duration IMPORT_PERIOD = Duration.ofSeconds(10);
    private static final Duration RETENTION_PERIOD = Duration.ofHours(1);
    private static final Duration STUCK_TRANSFER_PERIOD = Duration.ofMinutes(15);

    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);

    @BeforeEach
    void stopSchedulerClock() {
        when(taskScheduler.getClock()).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void everyScheduler_firstTicksAfterConfiguredDelay() {
        startSchedulers(Map.of("projectx.import.initial-delay", "7m"));

        Instant firstTick = NOW.plus(Duration.ofMinutes(7));
        assertThat(firstTicksByPeriod()).isEqualTo(Map.of(
                IMPORT_PERIOD, firstTick, RETENTION_PERIOD, firstTick, STUCK_TRANSFER_PERIOD, firstTick));
    }

    /** Без свойства — умолчание записи, полминуты: планировщик и запись читают одну константу. */
    @Test
    void withoutProperty_everySchedulerWaitsDefaultDelay() {
        startSchedulers(Map.of());

        Instant firstTick = NOW.plus(Duration.ofSeconds(30));
        assertThat(firstTicksByPeriod()).isEqualTo(Map.of(
                IMPORT_PERIOD, firstTick, RETENTION_PERIOD, firstTick, STUCK_TRANSFER_PERIOD, firstTick));
    }

    /**
     * Поднимает и сразу закрывает контекст: задачи заглушке передаются на
     * {@code ContextRefreshedEvent}, дальше контекст не нужен.
     */
    private void startSchedulers(Map<String, Object> properties) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
            context.register(Scheduling.class, ReferenceImportScheduler.class, PersonalDataRetention.class,
                    StuckTransferAlert.class, AgedInboundMessages.class);
            context.registerBean(SchedulingConfigurer.class,
                    () -> registrar -> registrar.setTaskScheduler(taskScheduler));
            context.registerBean(InboundMessageRepository.class, () -> mock(InboundMessageRepository.class));
            context.registerBean(ReferenceImportService.class, () -> mock(ReferenceImportService.class));
            context.registerBean(InboundMessageService.class, () -> mock(InboundMessageService.class));
            context.registerBean(ImportProperties.class, SchedulersFirstTickTest::importProperties);
            context.registerBean(Clock.class, Clock::systemUTC);
            context.refresh();
        }
    }

    /** @return момент первого запуска по периоду задачи; «запустить сразу» заглушке не поручали ни разу */
    private Map<Duration, Instant> firstTicksByPeriod() {
        ArgumentCaptor<Instant> firstTicks = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Duration> periods = ArgumentCaptor.forClass(Duration.class);
        verify(taskScheduler, times(3)).scheduleWithFixedDelay(any(Runnable.class), firstTicks.capture(),
                periods.capture());
        verify(taskScheduler, never()).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));

        List<Duration> scheduledPeriods = periods.getAllValues();
        List<Instant> scheduledFirstTicks = firstTicks.getAllValues();
        return IntStream.range(0, scheduledPeriods.size()).boxed()
                .collect(Collectors.toMap(scheduledPeriods::get, scheduledFirstTicks::get));
    }

    private static ImportProperties importProperties() {
        return new ImportProperties(IMPORT_PERIOD, Duration.ofSeconds(30), 100, Duration.ofHours(1),
                Duration.ofDays(30), 500);
    }

    @EnableScheduling
    static class Scheduling {
    }
}
