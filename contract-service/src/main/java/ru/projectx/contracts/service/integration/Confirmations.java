package ru.projectx.contracts.service.integration;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.connection.CorrelationData.Confirm;
import ru.projectx.contracts.service.outbox.PublishOutcome;

final class Confirmations {

    private Confirmations() {
    }

    static PublishOutcome await(CorrelationData correlation, Duration timeout) {
        try {
            Confirm confirm = correlation.getFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return outcome(confirm, Optional.ofNullable(correlation.getReturned()));
        }
        catch (TimeoutException | ExecutionException | InterruptedException failure) {
            return notConfirmed(failure, timeout);
        }
    }

    private static PublishOutcome notConfirmed(Exception failure, Duration timeout) {
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return new PublishOutcome.Interrupted();
        }
        if (failure instanceof TimeoutException) {
            return new PublishOutcome.Unconfirmed(timeout);
        }
        return new PublishOutcome.Rejected("ожидание подтверждения брокера завершилось ошибкой: "
                + Optional.ofNullable(failure.getCause()).orElse(failure).getClass().getName());
    }

    private static PublishOutcome outcome(Confirm confirm, Optional<ReturnedMessage> returned) {
        if (returned.isPresent()) {
            return new PublishOutcome.Refused("брокер вернул сообщение: " + returned.get().getReplyText()
                    + " (" + returned.get().getReplyCode() + ")");
        }
        return confirm.ack() ? new PublishOutcome.Delivered() : new PublishOutcome.Rejected(
                "брокер отклонил сообщение (nack)" + Optional.ofNullable(confirm.reason())
                        .map(reason -> ": " + reason).orElse(""));
    }
}
