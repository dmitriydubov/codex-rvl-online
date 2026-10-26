package ru.projectx.contracts.service.outbox;

public sealed interface OutboundBody {

    record Complete(String payload) implements OutboundBody {
    }

    record Withheld(PublishOutcome outcome) implements OutboundBody {
    }
}
