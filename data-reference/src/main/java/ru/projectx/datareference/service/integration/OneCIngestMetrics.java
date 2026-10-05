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

/**
 * Счётчики приёма из 1С:УСК для мониторинга: чем кончилась каждая доставка и сколько раз приём вставал на паузу.
 *
 * <p>Встроенный таймер слушателя Spring AMQP ({@code spring.rabbitmq.listener}) этого не показывает: для него
 * успешна любая доставка, после которой слушатель не бросил исключение, а так кончаются и потеря сообщения без
 * {@code message-id}, и возврат в очередь, и пауза приёма. Здесь исход называет тот код, который его выбрал.
 *
 * <p>Метки — поток, очередь и исход. Все три принимают немного заранее известных значений. Идентификатора
 * сообщения и тела в метках нет и быть не должно: каждое новое значение метки — новый ряд в Prometheus.
 *
 * <p>Ряды заводятся при создании нулями на каждую очередь из настроек и каждый исход: нулевой ряд есть с самого
 * старта, и правило «потеряно хоть одно сообщение» видит первую же потерю как рост с нуля, а отсутствующий ряд
 * неотличим от сломанного экспорта. Счёт доставки из этих очередей поэтому в реестр не обращается — только
 * увеличивает готовый счётчик.
 */
@Component
class OneCIngestMetrics {

    static final String DELIVERIES = "onec.ingest.deliveries";
    static final String PAUSES = "onec.ingest.pauses";

    /** Очередь неизвестна только у сообщения не из контейнера слушателя; метка не может быть пустой. */
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

    /** Считает доставку с её исходом. Доставку не из очереди настроек — под своей очередью или {@code unknown}. */
    void record(OneCDelivery delivery, IngestOutcome outcome) {
        SeriesKey key = new SeriesKey(
                delivery.stream(), Optional.ofNullable(delivery.queue()).orElse(UNKNOWN_QUEUE), outcome);
        Optional.ofNullable(deliveries.get(key))
                .orElseGet(() -> key.register(registry))
                .increment();
    }

    /** Считает остановку потребителей всех очередей из-за недоступной базы. */
    void paused() {
        pauses.increment();
    }

    /** Ряд счётчика доставок: поток, очередь и исход. */
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
