package ru.projectx.contracts.service.integration.consumer;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.broker.InternalBrokerProperties;
import ru.projectx.contracts.config.broker.OneCBrokerProperties;

@Component
public class InboxQueues {

    private final List<InboxQueue> queues;

    public InboxQueues(InternalBrokerProperties internal, OneCBrokerProperties onec,
            List<AdditionalInboxQueue> additional) {
        this.queues = Stream.concat(
                Stream.of(new InboxQueue(internal.queues().taskChanges(), internal.listenerAutoStartup()),
                        new InboxQueue(onec.queues().lmPolicyIssues(), onec.listenerAutoStartup()),
                        new InboxQueue(onec.queues().kmContractRegistrations(), onec.listenerAutoStartup())),
                additional.stream().map(queue -> new InboxQueue(queue.name(), queue.consumersEnabled())))
                .toList();
    }

    public List<String> names() {
        return queues.stream().map(InboxQueue::name).toList();
    }

    public Set<String> consumed() {
        return queues.stream().filter(InboxQueue::consumersEnabled).map(InboxQueue::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    record InboxQueue(String name, boolean consumersEnabled) {
    }
}
