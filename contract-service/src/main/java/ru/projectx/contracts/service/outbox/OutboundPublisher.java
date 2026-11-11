package ru.projectx.contracts.service.outbox;

import java.util.Collection;
import java.util.Set;

public interface OutboundPublisher {

    PublishOutcome publish(OutboundMessage message);

    Collection<Set<String>> eventTypesByBroker();
}
