package ru.projectx.contracts.service.integration.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;

class InboxQueueConsumersTest {

    private final InboxQueueContainers containers = mock();
    private final InboxConsumerSubscriptions subscriptions = mock();

    @Test
    void consumersNotExpected_noQueueIsMissingAConsumer() {
        given(containers.consumersExpected()).willReturn(false);

        assertThat(consumers(true, true).queuesWithoutConsumer()).isEmpty();

        verifyNoInteractions(subscriptions);
    }

    @Test
    void everyConsumedQueueSubscribed_noQueueIsMissingAConsumer() {
        given(containers.consumersExpected()).willReturn(true);
        given(subscriptions.subscribed(QueueContainersFixture.TASKS)).willReturn(true);
        given(subscriptions.subscribed(QueueContainersFixture.LM_RESULTS)).willReturn(true);
        given(subscriptions.subscribed(QueueContainersFixture.KM_RESULTS)).willReturn(true);

        assertThat(consumers(true, true).queuesWithoutConsumer()).isEmpty();
    }

    @Test
    void unsubscribedQueues_areNamedInAlphabeticalOrder() {
        given(containers.consumersExpected()).willReturn(true);
        given(subscriptions.subscribed(QueueContainersFixture.LM_RESULTS)).willReturn(true);

        assertThat(consumers(true, true).queuesWithoutConsumer())
                .containsExactly(QueueContainersFixture.KM_RESULTS, QueueContainersFixture.TASKS);
    }

    @Test
    void queuesOfABrokerWithListenersSwitchedOff_areNotExpected() {
        given(containers.consumersExpected()).willReturn(true);

        assertThat(consumers(true, false).queuesWithoutConsumer()).containsExactly(QueueContainersFixture.TASKS);
    }

    private InboxQueueConsumers consumers(boolean internalConsumers, boolean onecConsumers) {
        return new InboxQueueConsumers(QueueContainersFixture.queues(internalConsumers, onecConsumers), containers,
                subscriptions);
    }
}
