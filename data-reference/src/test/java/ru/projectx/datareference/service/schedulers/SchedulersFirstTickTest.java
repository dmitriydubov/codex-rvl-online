package ru.projectx.datareference.service.schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import ru.projectx.datareference.config.ImportProperties;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
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
 * периоды остались прежними. Какой планировщик стоит за каким периодом, узнаётся запуском
 * задачи — по запросу, который она делает к сырью: одного набора периодов мало, он не заметил бы
 * строк расписания, переставленных между классами.
 */
class SchedulersFirstTickTest {

    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");
    private static final Duration IMPORT_PERIOD = Duration.ofSeconds(10);
    private static final Duration RETENTION_PERIOD = Duration.ofHours(1);
    private static final Duration STUCK_TRANSFER_PERIOD = Duration.ofMinutes(15);

    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final InboundMessageRepository repository = mock(InboundMessageRepository.class);

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
     * Прежний период — у прежнего планировщика: разбор раз в десять секунд, срок хранения раз
     * в час, присмотр за передачами раз в четверть часа. Переставь строки расписания срока
     * хранения и присмотра между классами — набор периодов не изменится, а этот тест упадёт.
     */
    @Test
    void defaultPeriods_stayWithTheirSchedulers() {
        startSchedulers(Map.of());

        assertEveryPeriodRunsItsOwnScheduler(IMPORT_PERIOD, RETENTION_PERIOD, STUCK_TRANSFER_PERIOD);
    }

    /** Период каждого планировщика задаётся своим свойством: опечатка в имени молча оставила бы умолчание. */
    @Test
    void everyPeriod_isReadFromItsOwnProperty() {
        startSchedulers(Map.of(
                "projectx.import.poll-interval", "11s",
                "projectx.import.retention-poll-interval", "2h",
                "projectx.import.stuck-transfer-poll-interval", "16m"));

        assertEveryPeriodRunsItsOwnScheduler(Duration.ofSeconds(11), Duration.ofHours(2), Duration.ofMinutes(16));
    }

    /**
     * Поднимает и сразу закрывает контекст: задачи заглушке передаются на
     * {@code ContextRefreshedEvent}, дальше контекст не нужен. Пойманную задачу можно запустить
     * и после закрытия: она зовёт метод самого планировщика, а его соседи — заглушки этого теста.
     */
    private void startSchedulers(Map<String, Object> properties) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
            context.register(Scheduling.class, ReferenceImportScheduler.class, PersonalDataRetention.class,
                    StuckTransferAlert.class, AgedInboundMessages.class);
            context.registerBean(SchedulingConfigurer.class,
                    () -> registrar -> registrar.setTaskScheduler(taskScheduler));
            context.registerBean(InboundMessageRepository.class, () -> repository);
            context.registerBean(ReferenceImportService.class, () -> mock(ReferenceImportService.class));
            context.registerBean(InboundMessageService.class, () -> mock(InboundMessageService.class));
            context.registerBean(ImportProperties.class, SchedulersFirstTickTest::importProperties);
            context.registerBean(Clock.class, Clock::systemUTC);
            context.refresh();
        }
    }

    /** @return момент первого запуска по периоду задачи */
    private Map<Duration, Instant> firstTicksByPeriod() {
        return assignments().stream().collect(Collectors.toMap(Assignment::period, Assignment::firstTick));
    }

    /**
     * Запускает задачу каждого периода и узнаёт планировщик по его запросу к сырью: разбор берёт
     * дозревшие сообщения, срок хранения — тела полисов, присмотр — буферизованные части передач.
     * Выборки пусты, поэтому дальше запроса ни одна задача не идёт.
     */
    private void assertEveryPeriodRunsItsOwnScheduler(
            Duration importPeriod, Duration retentionPeriod, Duration stuckTransferPeriod) {

        Map<Duration, Runnable> tasks = assignments().stream()
                .collect(Collectors.toMap(Assignment::period, Assignment::task));
        assertThat(tasks.keySet()).containsExactlyInAnyOrder(importPeriod, retentionPeriod, stuckTransferPeriod);

        tasks.get(importPeriod).run();
        verify(repository).findDueForImport(any(Instant.class), any(Limit.class));
        tasks.get(retentionPeriod).run();
        verify(repository).findByStreamAndPayloadIsNotNullAndReceivedAtLessThan(
                eq(OneCStream.POLICIES), any(Instant.class), any(Limit.class));
        tasks.get(stuckTransferPeriod).run();
        verify(repository).findByStatusAndReceivedAtLessThan(eq(InboundStatus.BUFFERED), any(Instant.class));
    }

    /** @return все поручения заглушке; «запустить сразу» ей не поручали ни разу */
    private List<Assignment> assignments() {
        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Instant> firstTicks = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Duration> periods = ArgumentCaptor.forClass(Duration.class);
        verify(taskScheduler, times(3)).scheduleWithFixedDelay(tasks.capture(), firstTicks.capture(),
                periods.capture());
        verify(taskScheduler, never()).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));

        return IntStream.range(0, periods.getAllValues().size())
                .mapToObj(i -> new Assignment(tasks.getAllValues().get(i), firstTicks.getAllValues().get(i),
                        periods.getAllValues().get(i)))
                .toList();
    }

    private static ImportProperties importProperties() {
        return new ImportProperties(IMPORT_PERIOD, Duration.ofSeconds(30), 100, Duration.ofHours(1),
                Duration.ofDays(30), 500);
    }

    /** Одно поручение исполнителю: что запускать, когда впервые и с какой паузой между проходами. */
    private record Assignment(Runnable task, Instant firstTick, Duration period) {
    }

    @EnableScheduling
    static class Scheduling {
    }
}
