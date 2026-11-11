package ru.projectx.contracts.service.integration.consumer;

import java.time.Duration;
import java.util.List;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.ObjectProvider;
import ru.projectx.contracts.config.broker.BrokerConnectionProperties;
import ru.projectx.contracts.config.journal.InboxProperties;
import ru.projectx.contracts.config.broker.InternalBrokerProperties;
import ru.projectx.contracts.config.broker.OneCBrokerProperties;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;
import ru.projectx.contracts.support.TestLeadership;

final class QueueContainersFixture {

    static final String TASKS = "rvl_requests_to_rvl_contracts_task-changes";
    static final String LM_RESULTS = "1c-usk_to_rvl_contracts_lm-policy-changes";
    static final String KM_RESULTS = "1c-usk_to_rvl_contracts_km-contract-changes";

    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(30);

    private QueueContainersFixture() {
    }

    static InboxQueueContainers containers(ObjectProvider<RabbitListenerEndpointRegistry> registry,
            BackgroundLeadership leadership) {
        return containers(registry, leadership, queues(true, true));
    }

    static InboxQueueContainers containers(ObjectProvider<RabbitListenerEndpointRegistry> registry,
            BackgroundLeadership leadership, InboxQueues queues) {
        return new InboxQueueContainers(registry, queues, leadership, propertiesStoppingWithin(STOP_TIMEOUT));
    }

    static InboxQueueContainers containersStoppingWithin(ObjectProvider<RabbitListenerEndpointRegistry> registry,
            Duration stopTimeout) {
        return new InboxQueueContainers(registry, queues(true, true), TestLeadership.leader(),
                propertiesStoppingWithin(stopTimeout));
    }

    static InboxQueues queues(boolean internalConsumers, boolean onecConsumers) {
        return queues(internalConsumers, onecConsumers, List.of());
    }

    static InboxQueues queues(boolean internalConsumers, boolean onecConsumers, List<AdditionalInboxQueue> additional) {
        return new InboxQueues(
                new InternalBrokerProperties(connection(), internalConsumers,
                        new InternalBrokerProperties.Queues(TASKS, "rvl_contracts_to_rvl_requests_status-changes")),
                new OneCBrokerProperties(connection(), onecConsumers, new OneCBrokerProperties.Queues(
                        "rvl_contracts_to_1c-usk_km-contract-changes", KM_RESULTS,
                        "rvl_contracts_to_1c-usk_lm-policy-changes", LM_RESULTS)),
                additional);
    }

    static InboxProperties propertiesStoppingWithin(Duration stopTimeout) {
        return new InboxProperties(new InboxProperties.Processing(Duration.ofSeconds(10), 100),
                new InboxProperties.Retry(List.of(Duration.ofMinutes(1))),
                new InboxProperties.Retention(Duration.ofDays(30), Duration.ofHours(1), 500),
                new InboxProperties.Pause(Duration.ofSeconds(10), stopTimeout), Duration.ofSeconds(5));
    }

    private static BrokerConnectionProperties connection() {
        return new BrokerConnectionProperties("broker", 5672, "/", "user", "secret", Duration.ofSeconds(10),
                new BrokerConnectionProperties.Tls(false, true, true));
    }
}
