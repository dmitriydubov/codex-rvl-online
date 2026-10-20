package ru.projectx.datareference.service.integration;

import java.time.Duration;
import java.util.Map;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import ru.projectx.datareference.config.IngestProperties;
import ru.projectx.datareference.service.leadership.BackgroundLeadership;
import ru.projectx.datareference.support.TestLeadership;

final class QueueContainersFixture {

    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(30);

    static OneCQueueContainers containers(ObjectProvider<RabbitListenerEndpointRegistry> registry,
            BackgroundLeadership leadership) {

        return containers(registry, leadership, rabbitWithAutoStartup(true));
    }

    static OneCQueueContainers containers(ObjectProvider<RabbitListenerEndpointRegistry> registry,
            BackgroundLeadership leadership, ObjectProvider<RabbitProperties> rabbit) {

        return new OneCQueueContainers(registry, rabbit, leadership, ingestStoppingWithin(STOP_TIMEOUT));
    }

    static OneCQueueContainers containersStoppingWithin(ObjectProvider<RabbitListenerEndpointRegistry> registry,
            Duration stopTimeout) {

        return new OneCQueueContainers(registry, rabbitWithAutoStartup(true), TestLeadership.leader(),
                ingestStoppingWithin(stopTimeout));
    }

    private static IngestProperties ingestStoppingWithin(Duration stopTimeout) {
        return new IngestProperties(Duration.ofSeconds(5), Duration.ofSeconds(10), stopTimeout);
    }

    static ObjectProvider<RabbitProperties> rabbitWithAutoStartup(boolean autoStartup) {
        RabbitProperties rabbit = new RabbitProperties();
        rabbit.getListener().getSimple().setAutoStartup(autoStartup);
        return new StaticListableBeanFactory(Map.of("rabbitProperties", rabbit)).getBeanProvider(RabbitProperties.class);
    }

    static ObjectProvider<RabbitProperties> withoutBroker() {
        return new StaticListableBeanFactory().getBeanProvider(RabbitProperties.class);
    }

    private QueueContainersFixture() {
    }
}
