package ru.projectx.datareference.service.integration;

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

class OneCConsumerSubscriptionsTest {

    private static final String POLICIES = "1c-usk_to_rvl-online_policies";
    private static final String MKB10 = "1c-usk_to_rvl-online_mkb10-changes";

    private final OneCConsumerSubscriptions subscriptions = new OneCConsumerSubscriptions();
    private final AbstractMessageListenerContainer policiesContainer = container(POLICIES);

    @Test
    void noConfirmationFromBroker_queueIsNotSubscribed() {
        assertThat(subscriptions.subscribed(POLICIES)).isFalse();
    }

    @Test
    void brokerConfirmedSubscription_onlyThatQueueIsSubscribed() {
        subscriptions.consumeOk(consumeOk(openChannel(), POLICIES));

        assertThat(subscriptions.subscribed(POLICIES)).isTrue();
        assertThat(subscriptions.subscribed(MKB10)).isFalse();
    }

    @Test
    void subscriptionOnClosedChannel_isNotSubscribedWithoutAnyStopEvent() {
        Channel channel = openChannel();
        subscriptions.consumeOk(consumeOk(channel, POLICIES));

        when(channel.isOpen()).thenReturn(false);

        assertThat(subscriptions.subscribed(POLICIES)).isFalse();
    }

    @Test
    void consumerStopped_queueIsNoLongerSubscribed() {
        Channel channel = openChannel();
        subscriptions.consumeOk(consumeOk(channel, POLICIES));

        subscriptions.consumerStopped(new AsyncConsumerStoppedEvent(policiesContainer, consumerOn(channel)));

        assertThat(subscriptions.subscribed(POLICIES)).isFalse();
    }

    @Test
    void consumerReplacedAfterFailure_queueIsNoLongerSubscribedUntilNewConfirmation() {
        Channel failed = openChannel();
        subscriptions.consumeOk(consumeOk(failed, POLICIES));

        subscriptions.consumerRestarted(new AsyncConsumerRestartedEvent(
                policiesContainer, consumerOn(failed), consumerOn(openChannel())));

        assertThat(subscriptions.subscribed(POLICIES)).isFalse();
    }

    @Test
    void lateStopOfPreviousConsumer_keepsSubscriptionOfNewOne() {
        Channel previous = openChannel();
        Channel current = openChannel();
        subscriptions.consumeOk(consumeOk(previous, POLICIES));
        subscriptions.consumeOk(consumeOk(current, POLICIES));

        subscriptions.consumerStopped(new AsyncConsumerStoppedEvent(policiesContainer, consumerOn(previous)));
        subscriptions.consumerRestarted(new AsyncConsumerRestartedEvent(
                policiesContainer, consumerOn(previous), consumerOn(openChannel())));

        assertThat(subscriptions.subscribed(POLICIES)).isTrue();
    }

    @Test
    void stopOfOneContainer_keepsOtherQueuesSubscribed() {
        Channel policiesChannel = openChannel();
        subscriptions.consumeOk(consumeOk(policiesChannel, POLICIES));
        subscriptions.consumeOk(consumeOk(openChannel(), MKB10));

        subscriptions.consumerStopped(new AsyncConsumerStoppedEvent(policiesContainer, consumerOn(policiesChannel)));

        assertThat(subscriptions.subscribed(MKB10)).isTrue();
    }

    @Test
    void eventsFromUnknownSources_changeNothing() {
        Channel channel = openChannel();
        subscriptions.consumeOk(consumeOk(channel, POLICIES));

        subscriptions.consumeOk(new ConsumeOkEvent(new Object(), MKB10, "tag"));
        subscriptions.consumerStopped(new AsyncConsumerStoppedEvent(new Object(), consumerOn(channel)));
        subscriptions.consumerStopped(new AsyncConsumerStoppedEvent(policiesContainer, new Object()));

        assertThat(subscriptions.subscribed(MKB10)).isFalse();
        assertThat(subscriptions.subscribed(POLICIES)).isTrue();
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
