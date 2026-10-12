package ru.projectx.datareference.service.integration;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
class OneCIngestPauseGauge implements MeterBinder {

    static final String PAUSED = "onec.ingest.paused";

    private final OneCConsumerPause pause;

    OneCIngestPauseGauge(OneCConsumerPause pause) {
        this.pause = pause;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(PAUSED, pause, OneCIngestPauseGauge::pausedFlag)
                .description("Приём из 1С:УСК на паузе из-за недоступной базы: 1 — да, 0 — нет")
                .register(registry);
    }

    private static double pausedFlag(OneCConsumerPause pause) {
        return pause.paused().isPresent() ? 1 : 0;
    }
}
