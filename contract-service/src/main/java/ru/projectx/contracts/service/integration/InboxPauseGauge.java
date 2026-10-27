package ru.projectx.contracts.service.integration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.service.FlagGauges;

@Component
class InboxPauseGauge implements MeterBinder {

    static final String PAUSED = "contracts.inbox.paused";

    private final InboxConsumerPause pause;

    InboxPauseGauge(InboxConsumerPause pause) {
        this.pause = pause;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        FlagGauges.register(registry, PAUSED, "Приём из очередей на паузе из-за недоступной базы: 1 — да, 0 — нет",
                () -> pause.paused().isPresent());
    }
}
