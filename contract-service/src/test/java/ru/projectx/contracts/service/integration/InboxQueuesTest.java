package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InboxQueuesTest {

    @Test
    void names_listTheTaskQueueAndBothOneCResultQueuesInRegistrationOrder() {
        assertThat(QueueContainersFixture.queues(true, true).names()).containsExactly(
                QueueContainersFixture.TASKS, QueueContainersFixture.LM_RESULTS, QueueContainersFixture.KM_RESULTS);
    }

    @Test
    void consumed_followsTheListenerSwitchOfEachBroker() {
        assertThat(QueueContainersFixture.queues(true, true).consumed()).containsExactlyInAnyOrder(
                QueueContainersFixture.TASKS, QueueContainersFixture.LM_RESULTS, QueueContainersFixture.KM_RESULTS);
        assertThat(QueueContainersFixture.queues(true, false).consumed()).containsExactly(QueueContainersFixture.TASKS);
        assertThat(QueueContainersFixture.queues(false, true).consumed())
                .containsExactlyInAnyOrder(QueueContainersFixture.LM_RESULTS, QueueContainersFixture.KM_RESULTS);
        assertThat(QueueContainersFixture.queues(false, false).consumed()).isEmpty();
    }
}
