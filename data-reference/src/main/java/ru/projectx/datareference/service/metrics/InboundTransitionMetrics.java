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
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.entity.inbound.InboundStatus;
import ru.projectx.datareference.entity.inbound.OneCStream;

@Component
public class InboundTransitionMetrics {

    public static final String TRANSITIONS = "onec.inbound.transitions";

    private static final List<InboundStatus> TARGET_STATUSES = List.of(InboundStatus.PROCESSED, InboundStatus.SKIPPED,
            InboundStatus.BUFFERED, InboundStatus.AWAITING_PARENT, InboundStatus.RETRY, InboundStatus.FAILED);

    private final Map<SeriesKey, Counter> counters;

    public InboundTransitionMetrics(MeterRegistry registry) {
        this.counters = Stream.of(OneCStream.values())
                .flatMap(stream -> TARGET_STATUSES.stream().map(status -> new SeriesKey(stream, status)))
                .collect(Collectors.toUnmodifiableMap(Function.identity(), key -> key.register(registry)));
    }

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

    private record SeriesKey(OneCStream stream, InboundStatus status) {

        Counter register(MeterRegistry registry) {
            return Counter.builder(TRANSITIONS)
                    .description("Переходы сырых сообщений 1С в статус при разборе, после фиксации транзакции")
                    .tag("stream", stream.name())
                    .tag("status", status.name())
                    .register(registry);
        }
    }

    private record IncrementAfterCommit(Counter counter) implements TransactionSynchronization {

        @Override
        public void afterCommit() {
            counter.increment();
        }
    }
}
