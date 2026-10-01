package ru.projectx.datareference.service.integration;

import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.OneCQueueProperties;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Точки приёма из брокера 1С:УСК — по одной на каждую очередь {@link OneCQueue}. Доставка
 * уходит в {@link OneCQueueListener} уже с потоком своей очереди.
 *
 * <p>Каждая точка получает свой контейнер с настройками из {@code spring.rabbitmq.listener.simple}:
 * ручное подтверждение, один потребитель (порядок внутри потока значим), prefetch, автостарт
 * и отказ запуска при отсутствующей очереди. Сама точка ни одной из них не задаёт, иначе
 * перекрыла бы конфигурацию контура.
 *
 * <p>Идентификатор точки — имя её очереди: оно уникально и сразу говорит, чей это контейнер.
 */
@Component
public class OneCQueueEndpoints implements RabbitListenerConfigurer {

    private final OneCQueueListener listener;
    private final OneCQueueProperties queues;

    public OneCQueueEndpoints(OneCQueueListener listener, OneCQueueProperties queues) {
        this.listener = listener;
        this.queues = queues;
    }

    @Override
    public void configureRabbitListeners(RabbitListenerEndpointRegistrar registrar) {
        for (OneCQueue queue : OneCQueue.values()) {
            registrar.registerEndpoint(endpoint(queue.nameIn(queues), queue.stream()));
        }
    }

    private SimpleRabbitListenerEndpoint endpoint(String queueName, OneCStream stream) {
        SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
        endpoint.setId(queueName);
        endpoint.setQueueNames(queueName);
        endpoint.setMessageListener((ChannelAwareMessageListener) (message, channel) ->
                listener.receive(stream, message, channel));
        return endpoint;
    }
}
