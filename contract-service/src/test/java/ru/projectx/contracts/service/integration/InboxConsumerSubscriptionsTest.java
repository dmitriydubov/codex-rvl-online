package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.AsyncConsumerRestartedEvent;
import org.springframework.amqp.rabbit.listener.AsyncConsumerStoppedEvent;
import org.springframework.amqp.rabbit.listener.BlockingQueueConsumer;
import org.springframework.amqp.rabbit.listener.ConsumeOkEvent;

class InboxConsumerSubscriptionsTest {

    private static final String TASKS = QueueContainersFixture.TASKS;
    private static final String LM_RESULTS = QueueContainersFixture.LM_RESULTS;

    private final InboxConsumerSubscriptions subscriptions = new InboxConsumerSubscriptions();
    private final AbstractMessageListenerContainer tasksContainer = container(TASKS);

    @Test
    void noConfirmationFromBroker_queueIsNotSubscribed() {
        assertThat(subscriptions.subscribed(TASKS)).isFalse();
    }

    @Test
    void brokerConfirmedSubscription_onlyThatQueueIsSubscribed() {
        subscriptions.consumeOk(consumeOk(openChannel(), TASKS));

        assertThat(subscriptions.subscribed(TASKS)).isTrue();
        assertThat(subscriptions.subscribed(LM_RESULTS)).isFalse();
    }

    @Test
    void subscriptionOnClosedChannel_isNotSubscribedWithoutAnyStopEvent() {
        Channel channel = openChannel();
        subscriptions.consumeOk(consumeOk(channel, TASKS));

        when(channel.isOpen()).thenReturn(false);

        assertThat(subscriptions.subscribed(TASKS)).isFalse();
    }

    @Test
    void consumerStopped_queueIsNoLongerSubscribed() {
        Channel channel = openChannel();
        subscriptions.consumeOk(consumeOk(channel, TASKS));

        subscriptions.consumerStopped(new AsyncConsumerStoppedEvent(tasksContainer, consumerOn(channel)));

        assertThat(subscriptions.subscribed(TASKS)).isFalse();
    }

    @Test
    void consumerReplacedAfterFailure_queueIsNoLongerSubscribedUntilNewConfirmation() {
        Channel failed = openChannel();
        subscriptions.consumeOk(consumeOk(failed, TASKS));

        subscriptions.consumerRestarted(new AsyncConsumerRestartedEvent(
                tasksContainer, consumerOn(failed), consumerOn(openChannel())));

        assertThat(subscriptions.subscribed(TASKS)).isFalse();
    }

    @Test
    void lateStopOfPreviousConsumer_keepsSubscriptionOfNewOne() {
        Channel previous = openChannel();
        Channel current = openChannel();
        subscriptions.consumeOk(consumeOk(previous, TASKS));
        subscriptions.consumeOk(consumeOk(current, TASKS));

        subscriptions.consumerStopped(new AsyncConsumerStoppedEvent(tasksContainer, consumerOn(previous)));
        subscriptions.consumerRestarted(new AsyncConsumerRestartedEvent(
                tasksContainer, consumerOn(previous), consumerOn(openChannel())));

        assertThat(subscriptions.subscribed(TASKS)).isTrue();
    }

    @Test
    void stopOfOneContainer_keepsOtherQueuesSubscribed() {
        Channel tasksChannel = openChannel();
        subscriptions.consumeOk(consumeOk(tasksChannel, TASKS));
        subscriptions.consumeOk(consumeOk(openChannel(), LM_RESULTS));

        subscriptions.consumerStopped(new AsyncConsumerStoppedEvent(tasksContainer, consumerOn(tasksChannel)));

        assertThat(subscriptions.subscribed(LM_RESULTS)).isTrue();
    }

    @Test
    void eventsFromUnknownSources_changeNothing() {
        Channel channel = openChannel();
        subscriptions.consumeOk(consumeOk(channel, TASKS));

        subscriptions.consumeOk(new ConsumeOkEvent(new Object(), LM_RESULTS, "tag"));
        subscriptions.consumerStopped(new AsyncConsumerStoppedEvent(new Object(), consumerOn(channel)));
        subscriptions.consumerStopped(new AsyncConsumerStoppedEvent(tasksContainer, new Object()));

        assertThat(subscriptions.subscribed(LM_RESULTS)).isFalse();
        assertThat(subscriptions.subscribed(TASKS)).isTrue();
    }

    @Test
    void confirmationWithoutChannel_isNotASubscription() {
        subscriptions.consumeOk(new ConsumeOkEvent(new DefaultConsumer(null), TASKS, "tag"));

        assertThat(subscriptions.subscribed(TASKS)).isFalse();
    }

    private static Channel openChannel() {
        Channel channel = mock(Channel.class);
        when(channel.isOpen()).thenReturn(true);
        return channel;
    }

    private static ConsumeOkEvent consumeOk(Channel channel, String queue) {
        return new ConsumeOkEvent(new DefaultConsumer(channel), queue, "amq.ctag-" + queue);
    }

    private static BlockingQueueConsumer consumerOn(Channel channel) {
        BlockingQueueConsumer consumer = mock(BlockingQueueConsumer.class);
        when(consumer.getChannel()).thenReturn(channel);
        return consumer;
    }

    private static AbstractMessageListenerContainer container(String queue) {
        AbstractMessageListenerContainer container = mock(AbstractMessageListenerContainer.class);
        when(container.getQueueNames()).thenReturn(new String[] {queue});
        return container;
    }
}
