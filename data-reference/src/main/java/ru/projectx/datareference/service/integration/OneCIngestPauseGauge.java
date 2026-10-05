package ru.projectx.datareference.service.integration;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * Стоит ли приём из 1С:УСК на паузе прямо сейчас: 1 — потребители очередей остановлены, потому что база не
 * принимает записи, 0 — паузы нет. Пауза та же, что компонент {@code onecIngest} показывает в ручке здоровья, но в
 * виде ряда Prometheus, на который удобно поставить правило оповещения. Очередь без потребителя по другой причине
 * (потребитель не поднялся или очередь пропала на брокере) этот ряд не отражает: её видно только в ручке здоровья.
 * Значение читается при каждом скрейпе из памяти, к базе и брокеру он не ходит.
 */
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
