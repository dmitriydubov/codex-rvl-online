package ru.projectx.contracts.service.usk;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class UskPreparationMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final UskPreparationMetrics metrics = new UskPreparationMetrics(registry);

    @Test
    void countFailed_incrementsCounterTaggedWithTheEventTypeThatWasNotPrepared() {
        metrics.countFailed("KmContractSubmitted");
        metrics.countFailed("KmContractSubmitted");
        metrics.countFailed("LmPolicyRequested");

        assertThat(registry.get("contracts.usk.preparation.failed").tag("event_type", "KmContractSubmitted").counter()
                .count()).isEqualTo(2);
        assertThat(registry.get("contracts.usk.preparation.failed").tag("event_type", "LmPolicyRequested").counter()
                .count()).isEqualTo(1);
        assertThat(registry.get("contracts.usk.preparation.failed").counter().getId().getDescription())
                .isEqualTo("записи о выпуске, передача которых в 1С:УСК не подготовлена из-за внутренней ошибки");
    }
}
