package ru.projectx.datareference.service.integration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.Objects;
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
 * <p>Ряды заводятся при старте нулями на каждую очередь из настроек и каждый исход. Ряд, который появляется сразу
 * с единицей, Prometheus не считает приростом, и правило «потеряно хоть одно сообщение» пропустило бы первую же
 * потерю.
 */
@Component
class OneCIngestMetrics implements MeterBinder {

    static final String DELIVERIES = "onec.ingest.deliveries";
    static final String PAUSES = "onec.ingest.pauses";

    /** Очередь неизвестна только у сообщения не из контейнера слушателя; метка не может быть пустой. */
    private static final String UNKNOWN_QUEUE = "unknown";

    private final MeterRegistry registry;
    private final OneCQueueProperties queues;

    OneCIngestMetrics(MeterRegistry registry, OneCQueueProperties queues) {
        this.registry = registry;
        this.queues = queues;
    }

    /** Заводит нулевые ряды доставок на каждую очередь и каждый исход и ряд остановок. */
    @Override
    public void bindTo(MeterRegistry target) {
        for (OneCQueue queue : OneCQueue.values()) {
            for (IngestOutcome outcome : IngestOutcome.values()) {
                deliveries(queue.stream(), queue.nameIn(queues), outcome).register(target);
            }
        }
        pauses().register(target);
    }

    /** Считает доставку с её исходом. */
    void record(OneCDelivery delivery, IngestOutcome outcome) {
        deliveries(delivery.stream(), Objects.requireNonNullElse(delivery.queue(), UNKNOWN_QUEUE), outcome)
                .register(registry)
                .increment();
    }

    /** Считает остановку потребителей всех очередей из-за недоступной базы. */
    void paused() {
        pauses().register(registry).increment();
    }

    private static Counter.Builder deliveries(OneCStream stream, String queue, IngestOutcome outcome) {
        return Counter.builder(DELIVERIES)
                .description("Доставки из очередей 1С:УСК по исходу: сохранено, дубль, карантин, потеряно без "
                        + "message-id, возвращено в очередь, отказ базы")
                .tag("stream", stream.name())
                .tag("queue", queue)
                .tag("outcome", outcome.tagValue());
    }

    private static Counter.Builder pauses() {
        return Counter.builder(PAUSES)
                .description("Остановки потребителей очередей 1С:УСК из-за того, что база не принимает записи");
    }
}
