package ru.projectx.datareference.service.integration;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
class OneCQueuesWithoutConsumerGauge implements MeterBinder {

    static final String QUEUES_WITHOUT_CONSUMER = "onec.ingest.queues.without.consumer";

    private final OneCQueueConsumers consumers;
    private final OneCConsumerPause pause;

    OneCQueuesWithoutConsumerGauge(OneCQueueConsumers consumers, OneCConsumerPause pause) {
        this.consumers = consumers;
        this.pause = pause;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(QUEUES_WITHOUT_CONSUMER, this, OneCQueuesWithoutConsumerGauge::count)
                .strongReference(true)
                .description("Очереди 1С:УСК, которые ведущий должен читать, но у которых нет подтверждённого брокером "
                        + "потребителя; на паузе из-за базы и на неведущем — 0")
                .register(registry);
    }

    private static double count(OneCQueuesWithoutConsumerGauge gauge) {
        if (gauge.pause.paused().isPresent()) {
            return 0;
        }
        return gauge.consumers.queuesWithoutConsumer().size();
    }
}
