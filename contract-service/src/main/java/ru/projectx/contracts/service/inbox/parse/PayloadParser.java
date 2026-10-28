package ru.projectx.contracts.service.inbox.parse;

@FunctionalInterface
public interface PayloadParser<T> {

    T parse(String payload);
}
