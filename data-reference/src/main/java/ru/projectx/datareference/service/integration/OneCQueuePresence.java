package ru.projectx.datareference.service.integration;

import com.rabbitmq.client.ShutdownSignalException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.RabbitUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
class OneCQueuePresence {

    private final ObjectProvider<RabbitTemplate> broker;

    OneCQueuePresence(ObjectProvider<RabbitTemplate> broker) {
        this.broker = broker;
    }

    OnBroker of(String queue) {
        return Optional.ofNullable(broker.getIfAvailable())
                .map(template -> declarePassively(template, queue))
                .orElse(OnBroker.UNREACHABLE);
    }

    private static OnBroker declarePassively(RabbitTemplate template, String queue) {
        try {
            template.execute(channel -> channel.queueDeclarePassive(queue));
            return OnBroker.PRESENT;
        }
        catch (AmqpException failed) {
            return declaredMissing(failed) ? OnBroker.ABSENT : OnBroker.UNREACHABLE;
        }
    }

    private static boolean declaredMissing(AmqpException failed) {
        return Stream.iterate((Throwable) failed, Objects::nonNull, Throwable::getCause)
                .anyMatch(cause -> cause instanceof ShutdownSignalException signal
                        && RabbitUtils.isPassiveDeclarationChannelClose(signal));
    }

    enum OnBroker {
        PRESENT, ABSENT, UNREACHABLE
    }
}
