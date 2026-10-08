package ru.projectx.datareference.service.integration;

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

/**
 * На какие очереди потребители сервиса подписаны прямо сейчас, по подтверждениям брокера: запущенный контейнер ещё не
 * значит подписку. Подписка снимается только для того канала, на котором её подтвердили, потому что остановка прежнего
 * потребителя может прийти после подтверждения нового.
 */
@Component
public class OneCConsumerSubscriptions {

    private final Map<String, Channel> channelsByQueue = new ConcurrentHashMap<>();

    /** Брокер подтвердил подписку потребителя на очередь. */
    @EventListener
    public void consumeOk(ConsumeOkEvent event) {
        if (event.getSource() instanceof DefaultConsumer consumer) {
            Optional.ofNullable(consumer.getChannel()).ifPresent(channel -> channelsByQueue.put(event.getQueue(), channel));
        }
    }

    /** Потребитель контейнера остановлен: при остановке контейнера или после неустранимого сбоя. */
    @EventListener
    public void consumerStopped(AsyncConsumerStoppedEvent event) {
        unsubscribe(event.getSource(), event.getConsumer());
    }

    /** Потребитель заменён новым после сбоя; новый станет подписанным, когда брокер это подтвердит. */
    @EventListener
    public void consumerRestarted(AsyncConsumerRestartedEvent event) {
        unsubscribe(event.getSource(), event.getOldConsumer());
    }

    /**
     * {@code true}, когда брокер подтвердил подписку потребителя на очередь и его канал открыт. Канал проверяется при
     * чтении, потому что известие об остановке иногда не приходит вовсе.
     */
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
