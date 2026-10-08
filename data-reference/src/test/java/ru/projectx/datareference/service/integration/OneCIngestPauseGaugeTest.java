package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Защищает флаг паузы приёма в метриках: он читается из состояния паузы при каждом снятии значения. */
class OneCIngestPauseGaugeTest {

    private static final OneCConsumerPause.Paused PAUSED = new OneCConsumerPause.Paused(
            new StorageOutage("08006", "DataAccessResourceFailureException"),
            Instant.parse("2026-10-05T12:00:00Z"),
            Set.of("1c-usk_to_rvl-online_policies"));

    private final OneCConsumerPause pause = mock(OneCConsumerPause.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void gauge_followsPauseState() {
        new OneCIngestPauseGauge(pause).bindTo(registry);
        Gauge paused = registry.get(OneCIngestPauseGauge.PAUSED).gauge();

        when(pause.paused()).thenReturn(Optional.empty());
        assertThat(paused.value()).isZero();

        when(pause.paused()).thenReturn(Optional.of(PAUSED));
        assertThat(paused.value()).isEqualTo(1.0);

        when(pause.paused()).thenReturn(Optional.empty());
        assertThat(paused.value()).isZero();
    }

    @Test
    void gauge_isDescribed() {
        new OneCIngestPauseGauge(pause).bindTo(registry);

        assertThat(registry.get(OneCIngestPauseGauge.PAUSED).gauge().getId().getDescription())
                .startsWith("Приём из 1С:УСК на паузе");
    }
}
