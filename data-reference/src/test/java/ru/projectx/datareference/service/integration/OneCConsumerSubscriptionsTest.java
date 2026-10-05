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

/**
 * Подписка потребителя на очередь 1С:УСК считается по подтверждению брокера, а снимается, когда этот же
 * потребитель остановлен или заменён после сбоя. Запоздалое известие об остановке прежнего потребителя не
 * снимает подписку нового: иначе очередь с работающим потребителем навсегда числилась бы без него.
 */
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
        subscriptions.consumeOk(consumeOk(mock(Channel.class), POLICIES));

        assertThat(subscriptions.subscribed(POLICIES)).isTrue();
        assertThat(subscriptions.subscribed(MKB10)).isFalse();
    }

    @Test
    void consumerStopped_queueIsNoLongerSubscribed() {
        Channel channel = mock(Channel.class);
        subscriptions.consumeOk(consumeOk(channel, POLICIES));

        subscriptions.consumerStopped(new AsyncConsumerStoppedEvent(policiesContainer, consumerOn(channel)));

        assertThat(subscriptions.subscribed(POLICIES)).isFalse();
    }

    @Test
    void consumerReplacedAfterFailure_queueIsNoLongerSubscribedUntilNewConfirmation() {
        Channel failed = mock(Channel.class);
        subscriptions.consumeOk(consumeOk(failed, POLICIES));

        subscriptions.consumerRestarted(new AsyncConsumerRestartedEvent(
                policiesContainer, consumerOn(failed), consumerOn(mock(Channel.class))));

        assertThat(subscriptions.subscribed(POLICIES)).isFalse();
    }

    @Test
    void lateStopOfPreviousConsumer_keepsSubscriptionOfNewOne() {
        Channel previous = mock(Channel.class);
        Channel current = mock(Channel.class);
        subscriptions.consumeOk(consumeOk(previous, POLICIES));
        subscriptions.consumeOk(consumeOk(current, POLICIES));

        subscriptions.consumerStopped(new AsyncConsumerStoppedEvent(policiesContainer, consumerOn(previous)));
        subscriptions.consumerRestarted(new AsyncConsumerRestartedEvent(
                policiesContainer, consumerOn(previous), consumerOn(mock(Channel.class))));

        assertThat(subscriptions.subscribed(POLICIES)).isTrue();
    }

    @Test
    void stopOfOneContainer_keepsOtherQueuesSubscribed() {
        Channel policiesChannel = mock(Channel.class);
        subscriptions.consumeOk(consumeOk(policiesChannel, POLICIES));
        subscriptions.consumeOk(consumeOk(mock(Channel.class), MKB10));

        subscriptions.consumerStopped(new AsyncConsumerStoppedEvent(policiesContainer, consumerOn(policiesChannel)));

        assertThat(subscriptions.subscribed(MKB10)).isTrue();
    }

    @Test
    void eventsFromUnknownSources_changeNothing() {
        Channel channel = mock(Channel.class);
        subscriptions.consumeOk(consumeOk(channel, POLICIES));

        subscriptions.consumeOk(new ConsumeOkEvent(new Object(), MKB10, "tag"));
        subscriptions.consumerStopped(new AsyncConsumerStoppedEvent(new Object(), consumerOn(channel)));
        subscriptions.consumerStopped(new AsyncConsumerStoppedEvent(policiesContainer, new Object()));

        assertThat(subscriptions.subscribed(MKB10)).isFalse();
        assertThat(subscriptions.subscribed(POLICIES)).isTrue();
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
