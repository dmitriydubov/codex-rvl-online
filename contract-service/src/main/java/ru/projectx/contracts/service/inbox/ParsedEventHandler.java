package ru.projectx.contracts.service.inbox;

public abstract class ParsedEventHandler<T> implements InboxEventHandler {

    private final PayloadParser<T> parser;

    protected ParsedEventHandler(PayloadParser<T> parser) {
        this.parser = parser;
    }

    @Override
    public final InboxWork prepare(String payload) {
        return work(parser.parse(payload));
    }

    protected abstract InboxWork work(T parsed);
}
