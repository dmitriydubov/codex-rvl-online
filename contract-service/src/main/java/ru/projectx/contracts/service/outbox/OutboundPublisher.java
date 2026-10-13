package ru.projectx.contracts.service.outbox;

public interface OutboundPublisher {

    PublishOutcome publish(OutboundMessage message);
}
