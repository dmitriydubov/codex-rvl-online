package ru.projectx.datareference.service.integration;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownSignalException;
import java.io.IOException;
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
            return Optional.ofNullable(template.execute(channel -> declaredBy(channel, queue)))
                    .orElse(OnBroker.UNREACHABLE);
        }
        catch (AmqpException failed) {
            return channelClose(failed).map(OneCQueuePresence::refusal).orElse(OnBroker.UNREACHABLE);
        }
    }

    private static OnBroker declaredBy(Channel channel, String queue) throws IOException {
        channel.queueDeclarePassive(queue);
        return OnBroker.PRESENT;
    }

    private static Optional<ShutdownSignalException> channelClose(Throwable failed) {
        return Stream.iterate(failed, Objects::nonNull, Throwable::getCause)
                .filter(ShutdownSignalException.class::isInstance)
                .map(ShutdownSignalException.class::cast)
                .filter(signal -> !signal.isHardError())
                .findFirst();
    }

    private static OnBroker refusal(ShutdownSignalException channelClose) {
        return RabbitUtils.isPassiveDeclarationChannelClose(channelClose) ? OnBroker.ABSENT : OnBroker.REFUSED;
    }

    enum OnBroker {
        PRESENT, ABSENT, REFUSED, UNREACHABLE
    }
}
