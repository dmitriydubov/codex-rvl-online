package ru.projectx.contracts.service.integration.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import ru.projectx.contracts.support.TestLeadership;

class InboxHealthIndicatorTest {

    private static final Instant SINCE = Instant.parse("2026-02-10T07:05:00Z");

    private final InboxConsumerPause pause = mock();
    private final InboxQueueConsumers consumers = mock();

    @BeforeEach
    void everyQueueHasAConsumer() {
        given(pause.paused()).willReturn(Optional.empty());
        given(consumers.queuesWithoutConsumer()).willReturn(List.of());
    }

    @Test
    void leaderWithEveryConsumerSubscribed_isUpAndNamesItsRole() {
        Health health = new InboxHealthIndicator(pause, consumers, TestLeadership.leader()).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsExactly(Map.entry("role", "leader"));
    }

    @Test
    void leaderWithAQueueWithoutConsumer_isDownNamingTheQueue() {
        given(consumers.queuesWithoutConsumer()).willReturn(List.of(QueueContainersFixture.LM_RESULTS));

        Health health = new InboxHealthIndicator(pause, consumers, TestLeadership.leader()).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("role", "leader")
                .containsEntry("queuesWithoutConsumer", List.of(QueueContainersFixture.LM_RESULTS))
                .doesNotContainKey("sqlstate");
    }

    @Test
    void standbyWithoutPause_isUpWithoutAlarm() {
        Health health = new InboxHealthIndicator(pause, consumers, TestLeadership.standby()).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("role", "standby").doesNotContainKey("sqlstate");
    }

    @Test
    void pausedIngest_isDownWithTheOutageDetailsAndWithoutTheQueueList() {
        given(consumers.queuesWithoutConsumer()).willReturn(List.of(QueueContainersFixture.TASKS));
        given(pause.paused()).willReturn(Optional.of(new InboxConsumerPause.Paused(
                new StorageOutage("55P03", "CannotAcquireLockException"), SINCE,
                List.of(QueueContainersFixture.TASKS))));

        Health health = new InboxHealthIndicator(pause, consumers, TestLeadership.leader()).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("role", "leader")
                .containsEntry("sqlstate", "55P03")
                .containsEntry("failure", "CannotAcquireLockException")
                .containsEntry("since", "2026-02-10T07:05:00Z")
                .containsEntry("queues", List.of(QueueContainersFixture.TASKS))
                .doesNotContainKey("queuesWithoutConsumer");
    }

    @Test
    void pausedStandby_isDownToo() {
        given(pause.paused()).willReturn(Optional.of(new InboxConsumerPause.Paused(
                new StorageOutage("08001", "DataAccessResourceFailureException"), SINCE, List.of())));

        Health health = new InboxHealthIndicator(pause, consumers, TestLeadership.standby()).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("role", "standby").containsEntry("sqlstate", "08001");
    }
}
