package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OneCQueuesWithoutConsumerGaugeTest {

    private static final OneCConsumerPause.Paused PAUSED = new OneCConsumerPause.Paused(
            new StorageOutage("08006", "DataAccessResourceFailureException"),
            Instant.parse("2026-10-28T12:00:00Z"),
            Set.of("q-policies"));

    private final OneCQueueConsumers consumers = mock(OneCQueueConsumers.class);
    private final OneCConsumerPause pause = mock(OneCConsumerPause.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private Gauge gauge;

    @BeforeEach
    void bind() {
        when(pause.paused()).thenReturn(Optional.empty());
        new OneCQueuesWithoutConsumerGauge(consumers, pause).bindTo(registry);
        gauge = registry.get(OneCQueuesWithoutConsumerGauge.QUEUES_WITHOUT_CONSUMER).gauge();
    }

    @Test
    void gauge_countsQueuesWithoutConsumer() {
        when(consumers.queuesWithoutConsumer()).thenReturn(List.of("q-mkb10", "q-policies"));

        assertThat(gauge.value()).isEqualTo(2.0);
    }

    @Test
    void everyQueueRead_isZero() {
        when(consumers.queuesWithoutConsumer()).thenReturn(List.of());

        assertThat(gauge.value()).isZero();
    }

    @Test
    void duringPause_isZeroWhateverTheQueuesSay() {
        when(consumers.queuesWithoutConsumer()).thenReturn(List.of("q-mkb10", "q-policies"));
        when(pause.paused()).thenReturn(Optional.of(PAUSED));

        assertThat(gauge.value()).isZero();

        when(pause.paused()).thenReturn(Optional.empty());
        assertThat(gauge.value()).isEqualTo(2.0);
    }

    @Test
    void gauge_isDescribedAndHasNoTags() {
        assertThat(gauge.getId().getDescription()).startsWith("Очереди 1С:УСК, которые ведущий должен читать");
        assertThat(gauge.getId().getTags()).isEmpty();
    }
}
