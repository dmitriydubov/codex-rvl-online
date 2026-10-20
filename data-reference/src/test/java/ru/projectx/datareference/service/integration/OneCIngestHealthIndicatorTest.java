package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import ru.projectx.datareference.support.TestLeadership;

@ExtendWith(MockitoExtension.class)
class OneCIngestHealthIndicatorTest {

    private static final Instant SINCE = Instant.parse("2026-10-04T12:00:00Z");

    @Mock
    private OneCConsumerPause pause;

    @Mock
    private OneCQueueConsumers consumers;

    @Test
    void everyQueueHasConsumer_isUpNamingTheLeaderRole() {
        when(pause.paused()).thenReturn(Optional.empty());
        when(consumers.queuesWithoutConsumer()).thenReturn(List.of());

        Health health = new OneCIngestHealthIndicator(pause, consumers, TestLeadership.leader()).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsOnly(entry("role", "leader"));
    }

    @Test
    void standbyExpectsNoConsumers_isUpNamingTheStandbyRole() {
        when(pause.paused()).thenReturn(Optional.empty());
        when(consumers.queuesWithoutConsumer()).thenReturn(List.of());

        Health health = new OneCIngestHealthIndicator(pause, consumers, TestLeadership.standby()).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsOnly(entry("role", "standby"));
    }

    @Test
    void queuesWithoutConsumer_isDownNamingThem() {
        when(pause.paused()).thenReturn(Optional.empty());
        when(consumers.queuesWithoutConsumer()).thenReturn(List.of("q-mkb10", "q-policies"));

        Health health = new OneCIngestHealthIndicator(pause, consumers, TestLeadership.leader()).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsOnlyKeys("queuesWithoutConsumer", "role")
                .containsEntry("role", "leader")
                .containsEntry("queuesWithoutConsumer", List.of("q-mkb10", "q-policies"));
    }

    @Test
    void consumersPaused_isDownWithSqlStateFailureSinceAndQueues() {
        when(pause.paused()).thenReturn(Optional.of(new OneCConsumerPause.Paused(
                new StorageOutage("08001", "CannotCreateTransactionException"), SINCE, Set.of("q-policies"))));

        lenient().when(consumers.queuesWithoutConsumer()).thenReturn(List.of("q-policies"));

        Health health = new OneCIngestHealthIndicator(pause, consumers, TestLeadership.leader()).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .doesNotContainKey("queuesWithoutConsumer")
                .containsEntry("role", "leader")
                .containsEntry("sqlstate", "08001")
                .containsEntry("failure", "CannotCreateTransactionException")
                .containsEntry("since", "2026-10-04T12:00:00Z")
                .containsEntry("queues", Set.of("q-policies"));
    }
}
