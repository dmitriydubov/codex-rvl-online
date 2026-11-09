package ru.projectx.contracts.service.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.OrderingProperties;
import ru.projectx.contracts.config.payment.PaymentSwitch;
import ru.projectx.contracts.exception.OrderingUnavailableException;

@Component
@ConditionalOnBooleanProperty(PaymentSwitch.ENABLED)
public class OrderingAttempts {

    private final RetryTemplate template;

    public OrderingAttempts(OrderingProperties settings) {
        this.template = new RetryTemplate(RetryPolicy.builder()
                .predicate(OrderingUnavailableException.class::isInstance)
                .maxRetries(settings.retry().attempts() - 1L)
                .delay(settings.retry().delay())
                .build());
    }

    public <T> T run(Retryable<T> attempt) {
        try {
            return template.execute(attempt);
        }
        catch (RetryException outcome) {
            throw lastFailure(outcome);
        }
    }

    private static RuntimeException lastFailure(RetryException outcome) {
        Throwable last = outcome.getCause();
        if (last instanceof Error error) {
            throw error;
        }
        return last instanceof RuntimeException runtime ? runtime : new IllegalStateException(last);
    }
}
