package ru.projectx.contracts.service.payment;

import java.util.function.Consumer;
import java.util.function.Function;

public record OrderingStep<T>(
        String notDone,
        Function<OrderingSession, T> attempt,
        Consumer<T> succeeded,
        Consumer<String> rejected) {
}
