package ru.projectx.datareference.service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.List;
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
 * считается сразу: записывать его тогда некому, кроме вызывающего, и откатываться нечему.
 *
 * <p>Метки — поток и новый статус, как они названы в таблице сырья. Идентификатор сообщения в метки не идёт. Ряды
 * заводятся при старте нулями на каждый поток и каждый статус, в который переводит разбор: ряд, появившийся сразу
 * с единицей, Prometheus приростом не считает.
 */
@Component
public class InboundTransitionMetrics implements MeterBinder {

    public static final String TRANSITIONS = "onec.inbound.transitions";

    /** Статусы, в которые сообщение переводит разбор. {@code NEW} ставит приём, его считают счётчики приёма. */
    private static final List<InboundStatus> TARGET_STATUSES = List.of(InboundStatus.PROCESSED, InboundStatus.SKIPPED,
            InboundStatus.BUFFERED, InboundStatus.RETRY, InboundStatus.FAILED);

    private final MeterRegistry registry;

    public InboundTransitionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Заводит нулевые ряды на каждый поток и каждый статус, в который переводит разбор. */
    @Override
    public void bindTo(MeterRegistry target) {
        for (OneCStream stream : OneCStream.values()) {
            for (InboundStatus status : TARGET_STATUSES) {
                transitions(stream, status).register(target);
            }
        }
    }

    /** Считает переход сообщения в статус, который у него сейчас, — после фиксации текущей транзакции. */
    public void record(InboundMessage message) {
        Counter transitions = transitions(message.receipt().stream(), message.outcome().status()).register(registry);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            transitions.increment();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new IncrementAfterCommit(transitions));
    }

    private static Counter.Builder transitions(OneCStream stream, InboundStatus status) {
        return Counter.builder(TRANSITIONS)
                .description("Переходы сырых сообщений 1С в статус при разборе, после фиксации транзакции")
                .tag("stream", stream.name())
                .tag("status", status.name());
    }

    /** Отложенное увеличение счётчика: только если транзакция зафиксирована. */
    private record IncrementAfterCommit(Counter counter) implements TransactionSynchronization {

        @Override
        public void afterCommit() {
            counter.increment();
        }
    }
}
