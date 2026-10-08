package ru.projectx.datareference.service.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Метрики накопления в таблице сырых сообщений 1С из снимка в памяти, который обновляет расписание: скрейп не ждёт
 * недоступную базу. Отставание снимка видно отдельной метрикой; пустой снимок помечен 1970 годом. Ряды заведены
 * на каждый поток и статус заранее, потому что пропавший ряд неотличим от сломанного экспорта.
 */
@Component
public class InboundBacklogMetrics implements MeterBinder {

    static final String MESSAGES = "onec.inbound.messages";
    static final String OLDEST_PENDING = "onec.inbound.pending.oldest.age";
    static final String STUCK_TRANSFERS = "onec.price.transfers.stuck";
    static final String OVERDUE_PAYLOADS = "onec.retention.overdue.payloads";
    static final String OVERDUE_QUARANTINE_BODIES = "onec.retention.overdue.quarantine.bodies";
    static final String OVERDUE_POLICIES = "policies.retention.overdue.personal.data";
    static final String STALENESS = "onec.inbound.backlog.staleness";

    /** Всё, что ещё не разобрано, и отказ, который ждёт человека. */
    private static final List<InboundStatus> GAUGED_STATUSES =
            List.of(InboundStatus.NEW, InboundStatus.RETRY, InboundStatus.BUFFERED, InboundStatus.AWAITING_PARENT,
                    InboundStatus.FAILED);

    private static final Logger log = LoggerFactory.getLogger(InboundBacklogMetrics.class);

    private final InboundBacklogReads reads;
    private final Clock clock;
    private volatile InboundBacklog backlog;

    public InboundBacklogMetrics(InboundBacklogReads reads, Clock clock) {
        this.reads = reads;
        this.clock = clock;
        this.backlog = InboundBacklog.empty(Instant.EPOCH);
    }

    /** Отказ базы не роняет задачу и оставляет прежний снимок; в журнал идёт только тип исключения. */
    public void refresh() {
        try {
            backlog = reads.read();
        }
        catch (RuntimeException unavailable) {
            log.warn("метрики очереди разбора 1С не обновлены, остаётся снимок от {}: {}",
                    backlog.takenAt(), unavailable.getClass().getName());
        }
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (OneCStream stream : OneCStream.values()) {
            bindStream(registry, stream);
        }
        gauge(registry, STUCK_TRANSFERS, "Передачи прайсов, которые ждут своих частей дольше порога тревоги",
                current -> current.stuckTransfers());
        gauge(registry, OVERDUE_PAYLOADS, "Тела сообщений с ПДн старше срока хранения, ещё не стёртые",
                current -> current.overdue().payloads());
        gauge(registry, OVERDUE_QUARANTINE_BODIES, "Тела карантинных сообщений старше срока хранения, ещё не стёртые",
                current -> current.overdue().quarantineBodies());
        gauge(registry, OVERDUE_POLICIES,
                "Полисы, у которых срок хранения ФИО, даты рождения и почты истёк, а они ещё не стёрты",
                current -> current.overdue().policies());
        TimeGauge.builder(STALENESS, this, TimeUnit.SECONDS, metrics -> metrics.secondsSince(metrics.backlog.takenAt()))
                .description("Сколько секунд назад обновлены метрики очереди разбора 1С")
                .register(registry);
    }

    private void bindStream(MeterRegistry registry, OneCStream stream) {
        for (InboundStatus status : GAUGED_STATUSES) {
            Gauge.builder(MESSAGES, this, metrics -> metrics.backlog.messages(stream, status))
                    .description("Сырые сообщения 1С по потоку и статусу: ждут разбора (NEW, RETRY), ждут частей "
                            + "передачи (BUFFERED), ждут родителя (AWAITING_PARENT), отказ (FAILED)")
                    .tag("stream", stream.name())
                    .tag("status", status.name())
                    .register(registry);
        }
        TimeGauge.builder(OLDEST_PENDING, this, TimeUnit.SECONDS, metrics -> metrics.oldestPendingAge(stream))
                .description("Сколько секунд ждёт разбора самое давнее сообщение потока (NEW или RETRY); 0 — не ждёт ни одно")
                .tag("stream", stream.name())
                .register(registry);
    }

    private void gauge(MeterRegistry registry, String name, String description, ToDoubleFunction<InboundBacklog> value) {
        Gauge.builder(name, this, metrics -> value.applyAsDouble(metrics.backlog))
                .description(description)
                .register(registry);
    }

    /** Возраст считается на момент скрейпа, а не снимка, чтобы давность росла и между обновлениями. */
    private double oldestPendingAge(OneCStream stream) {
        return backlog.oldestPending(stream).map(this::secondsSince).orElse(0.0);
    }

    private double secondsSince(Instant moment) {
        return Duration.between(moment, clock.instant()).toMillis() / (double) TimeUnit.SECONDS.toMillis(1);
    }
}
