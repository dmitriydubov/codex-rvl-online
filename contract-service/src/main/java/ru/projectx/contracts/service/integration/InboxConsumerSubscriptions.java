package ru.projectx.contracts.service.integration;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.AsyncConsumerRestartedEvent;
import org.springframework.amqp.rabbit.listener.AsyncConsumerStoppedEvent;
import org.springframework.amqp.rabbit.listener.BlockingQueueConsumer;
import org.springframework.amqp.rabbit.listener.ConsumeOkEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class InboxConsumerSubscriptions {

    private final Map<String, Channel> channelsByQueue = new ConcurrentHashMap<>();

    @EventListener
    public void consumeOk(ConsumeOkEvent event) {
        if (event.getSource() instanceof DefaultConsumer consumer) {
            Optional.ofNullable(consumer.getChannel()).ifPresent(channel -> channelsByQueue.put(event.getQueue(), channel));
        }
    }

    @EventListener
    public void consumerStopped(AsyncConsumerStoppedEvent event) {
        unsubscribe(event.getSource(), event.getConsumer());
    }

    @EventListener
    public void consumerRestarted(AsyncConsumerRestartedEvent event) {
        unsubscribe(event.getSource(), event.getOldConsumer());
    }

    public boolean subscribed(String queue) {
        return Optional.ofNullable(channelsByQueue.get(queue)).filter(Channel::isOpen).isPresent();
    }

    private void unsubscribe(Object container, Object consumer) {
        if (container instanceof AbstractMessageListenerContainer listeners
                && consumer instanceof BlockingQueueConsumer stopped) {
            Arrays.stream(listeners.getQueueNames())
                    .forEach(queue -> channelsByQueue.remove(queue, stopped.getChannel()));
        }
    }
}
