package ru.projectx.contracts.service.inbox;

@FunctionalInterface
public interface PayloadParser<T> {

    T parse(String payload);
}
