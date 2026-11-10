package ru.projectx.contracts.service.integration.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

    @Test
    void additionalQueues_followTheContourQueuesInTheirOwnOrder() {
        InboxQueues queues = QueueContainersFixture.queues(true, true,
                List.of(additional("order.event.rvl.topic", true), additional("order.event.other.topic", true)));

        assertThat(queues.names()).containsExactly(QueueContainersFixture.TASKS, QueueContainersFixture.LM_RESULTS,
                QueueContainersFixture.KM_RESULTS, "order.event.rvl.topic", "order.event.other.topic");
    }

    @Test
    void additionalQueue_isConsumedOnlyWhenItsOwnSwitchIsOn() {
        assertThat(QueueContainersFixture.queues(false, false, List.of(additional("order.event.rvl.topic", true)))
                .consumed()).containsExactly("order.event.rvl.topic");
        assertThat(QueueContainersFixture.queues(true, false, List.of(additional("order.event.rvl.topic", false)))
                .consumed()).containsExactly(QueueContainersFixture.TASKS);
    }

    private static AdditionalInboxQueue additional(String name, boolean consumersEnabled) {
        return new AdditionalInboxQueue(name, consumersEnabled);
    }
}
