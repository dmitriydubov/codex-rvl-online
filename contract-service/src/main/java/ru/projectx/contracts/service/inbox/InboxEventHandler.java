package ru.projectx.contracts.service.inbox;

public interface InboxEventHandler {

    String eventType();

    default InboxOrdering ordering() {
        return InboxOrdering.BY_REQUEST_VERSION;
    }

    InboxWork prepare(String payload);
}
