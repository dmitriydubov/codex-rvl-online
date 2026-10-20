package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import ru.projectx.datareference.support.TestLeadership;

@ExtendWith(MockitoExtension.class)
class OneCIngestPauseViewsTest {

    private static final StorageOutage CONNECTION_LOST = new StorageOutage("08006", "DataAccessResourceFailureException");

    @Mock
    private ObjectProvider<RabbitListenerEndpointRegistry> registryProvider;

    @Mock
    private IngestStorageProbe probe;

    @Mock
    private OneCQueueConsumers consumers;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private OneCConsumerPause pause;
    private OneCIngestHealthIndicator health;
    private Gauge paused;

    @BeforeEach
    void healthAndGaugeOverOnePause() {
        pause = new OneCConsumerPause(QueueContainersFixture.containers(registryProvider, TestLeadership.leader()), probe,
                Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC), IngestTestMetrics.on(meters));
        health = new OneCIngestHealthIndicator(pause, consumers, TestLeadership.leader());
        new OneCIngestPauseGauge(pause).bindTo(meters);
        paused = meters.get(OneCIngestPauseGauge.PAUSED).gauge();
        when(consumers.queuesWithoutConsumer()).thenReturn(List.of());
    }

    @Test
    void pauseAndResume_seenAlikeByHealthAndGauge() {
        assertThat(paused.value()).isZero();
        assertThat(health.health().getStatus()).isEqualTo(Status.UP);

        pause.pause(CONNECTION_LOST);

        Health duringPause = health.health();
        assertThat(paused.value()).isEqualTo(1.0);
        assertThat(duringPause.getStatus()).isEqualTo(Status.DOWN);
        assertThat(duringPause.getDetails()).containsEntry("sqlstate", "08006");

        when(probe.storageAvailable()).thenReturn(true);
        pause.resumeIfStorageAvailable();

        assertThat(paused.value()).isZero();
        assertThat(health.health().getStatus()).isEqualTo(Status.UP);
    }
}
