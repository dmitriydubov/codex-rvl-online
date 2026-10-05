package ru.projectx.datareference.service.integration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import ru.projectx.datareference.config.OneCQueueProperties;

/** Счётчики приёма для тестов пакета: реестр в памяти и имена очередей, как в тестах точек приёма. */
final class IngestTestMetrics {

    static final OneCQueueProperties QUEUES = new OneCQueueProperties(
            "q-policies", "q-insurance-contracts", "q-contragents", "q-mkb10", "q-lpu-contracts",
            "q-programs", "q-appeal-types", "q-lpu-contacts", "q-price-lists", "q-price-lists-final",
            "q-contragent-services");

    private IngestTestMetrics() {
    }

    /** Счётчики над данным реестром. */
    static OneCIngestMetrics on(MeterRegistry registry) {
        return new OneCIngestMetrics(registry, QUEUES);
    }

    /** Счётчики над своим реестром, когда тесту они не важны. */
    static OneCIngestMetrics discarded() {
        return on(new SimpleMeterRegistry());
    }
}
