package ru.projectx.datareference.service.integration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.OneCQueueProperties;
import ru.projectx.datareference.entity.OneCStream;

@Component
class OneCIngestMetrics {

    static final String DELIVERIES = "onec.ingest.deliveries";
    static final String PAUSES = "onec.ingest.pauses";

    private static final String UNKNOWN_QUEUE = "unknown";

    private final MeterRegistry registry;
    private final Map<SeriesKey, Counter> deliveries;
    private final Counter pauses;

    OneCIngestMetrics(MeterRegistry registry, OneCQueueProperties queues) {
        this.registry = registry;
        this.deliveries = Stream.of(OneCQueue.values())
                .flatMap(queue -> Stream.of(IngestOutcome.values())
                        .map(outcome -> new SeriesKey(queue.stream(), queue.nameIn(queues), outcome)))
                .collect(Collectors.toUnmodifiableMap(Function.identity(), key -> key.register(registry)));
        this.pauses = Counter.builder(PAUSES)
                .description("Остановки потребителей очередей 1С:УСК из-за того, что база не принимает записи")
                .register(registry);
    }

    void record(OneCDelivery delivery, IngestOutcome outcome) {
        SeriesKey key = new SeriesKey(
                delivery.stream(), Optional.ofNullable(delivery.queue()).orElse(UNKNOWN_QUEUE), outcome);
        Optional.ofNullable(deliveries.get(key))
                .orElseGet(() -> key.register(registry))
                .increment();
    }

    void paused() {
        pauses.increment();
    }

    private record SeriesKey(OneCStream stream, String queue, IngestOutcome outcome) {

        Counter register(MeterRegistry registry) {
            return Counter.builder(DELIVERIES)
                    .description("Доставки из очередей 1С:УСК по исходу: сохранено, дубль, карантин, потеряно без "
                            + "message-id, возвращено в очередь, отказ базы")
                    .tag("stream", stream.name())
                    .tag("queue", queue)
                    .tag("outcome", outcome.tagValue())
                    .register(registry);
        }
    }
}
