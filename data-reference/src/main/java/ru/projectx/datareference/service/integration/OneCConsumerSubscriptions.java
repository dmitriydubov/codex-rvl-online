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
 * На какие очереди брокера потребители сервиса подписаны прямо сейчас — по подтверждениям самого брокера.
 *
 * <p>Признак «контейнер слушателя запущен» для этого не годится: контейнер считается запущенным раньше, чем его
 * потребитель подписался, и остаётся запущенным, пока потребитель раз за разом не может найти пропавшую очередь.
 * Поэтому очередь считается подписанной, когда брокер подтвердил подписку, и перестаёт ею быть, когда потребитель
 * остановлен или заменён новым после сбоя (оборвалось соединение, брокер отменил подписку при удалении очереди).
 *
 * <p>Подтверждение подписки приходит из потока клиента брокера, остановка — из потока потребителя, и остановка
 * прежнего потребителя может прийти уже после подтверждения нового. Чтобы она не сняла чужую подписку, запоминается
 * канал, на котором брокер подтвердил подписку, и снимается подписка только того же канала.
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

    /** {@code true}, когда у очереди есть потребитель сервиса, подписку которого подтвердил брокер. */
    public boolean subscribed(String queue) {
        return channelsByQueue.containsKey(queue);
    }

    private void unsubscribe(Object container, Object consumer) {
        if (container instanceof AbstractMessageListenerContainer listeners
                && consumer instanceof BlockingQueueConsumer stopped) {
            Arrays.stream(listeners.getQueueNames())
                    .forEach(queue -> channelsByQueue.remove(queue, stopped.getChannel()));
        }
    }
}
