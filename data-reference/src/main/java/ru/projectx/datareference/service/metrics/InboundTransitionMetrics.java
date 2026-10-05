package ru.projectx.datareference.service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Счётчик переходов статусов сырых сообщений 1С при разборе: сколько сообщений каждого потока разобрано, пропущено,
 * отложено в буфер передачи, назначено на повтор и ушло в отказ.
 *
 * <p>Переход считается только после фиксации транзакции, в которой он записан. Статус ставится внутри транзакции
 * разбора, и она ещё может откатиться — например, если база отказала на самом коммите. Тогда сообщение остаётся в
 * прежнем статусе и разбирается снова, а преждевременно посчитанный «разобрано» врал бы. Вне транзакции переход
 * считается сразу: записывать его тогда некому, кроме вызывающего, и откатываться нечему. Считается каждый статус,
 * записанный в зафиксированной транзакции, а не только последний: сообщение прайса, которое собрало свою передачу,
 * в одной транзакции попадает и в {@code BUFFERED}, и в итоговый статус передачи.
 *
 * <p>Метки — поток и новый статус, как они названы в таблице сырья. Идентификатор сообщения в метки не идёт. Ряды
 * заводятся при создании нулями на каждый поток и каждый статус, в который переводит разбор: нулевой ряд есть с
 * самого старта, и правило оповещения видит первое же увеличение. Поэтому {@link #record} в реестр не обращается и
 * транзакцию разбора уронить не может — только увеличивает готовый счётчик.
 */
@Component
public class InboundTransitionMetrics {

    public static final String TRANSITIONS = "onec.inbound.transitions";

    /** Статусы, в которые сообщение переводит разбор. {@code NEW} ставит приём, его считают счётчики приёма. */
    private static final List<InboundStatus> TARGET_STATUSES = List.of(InboundStatus.PROCESSED, InboundStatus.SKIPPED,
            InboundStatus.BUFFERED, InboundStatus.RETRY, InboundStatus.FAILED);

    private final Map<SeriesKey, Counter> counters;

    public InboundTransitionMetrics(MeterRegistry registry) {
        this.counters = Stream.of(OneCStream.values())
                .flatMap(stream -> TARGET_STATUSES.stream().map(status -> new SeriesKey(stream, status)))
                .collect(Collectors.toUnmodifiableMap(Function.identity(), key -> key.register(registry)));
    }

    /**
     * Считает переход сообщения в статус, который у него сейчас, — после фиксации текущей транзакции. Статус, в
     * который разбор не переводит, не считается.
     */
    public void record(InboundMessage message) {
        Optional.ofNullable(counters.get(new SeriesKey(message.receipt().stream(), message.outcome().status())))
                .ifPresent(InboundTransitionMetrics::incrementAfterCommit);
    }

    private static void incrementAfterCommit(Counter counter) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            counter.increment();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new IncrementAfterCommit(counter));
    }

    /** Ряд счётчика: поток и статус. */
    private record SeriesKey(OneCStream stream, InboundStatus status) {

        Counter register(MeterRegistry registry) {
            return Counter.builder(TRANSITIONS)
                    .description("Переходы сырых сообщений 1С в статус при разборе, после фиксации транзакции")
                    .tag("stream", stream.name())
                    .tag("status", status.name())
                    .register(registry);
        }
    }

    /** Отложенное увеличение счётчика: только если транзакция зафиксирована. */
    private record IncrementAfterCommit(Counter counter) implements TransactionSynchronization {

        @Override
        public void afterCommit() {
            counter.increment();
        }
    }
}
