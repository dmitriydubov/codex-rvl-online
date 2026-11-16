package ru.projectx.contracts.service.integration.listener;

@FunctionalInterface
public interface InboxEnvelopes {

    TaskEnvelope parse(byte[] body);
}
