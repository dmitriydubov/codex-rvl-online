package ru.projectx.contracts.service.integration;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import ru.projectx.contracts.config.ReferenceProperties;
import ru.projectx.contracts.exception.ReferenceUnavailableException;
import ru.projectx.contracts.exception.ReferenceUnavailableException.Reason;

/**
 * Ограниченные повторы чтения справочника. Исчерпанные повторы — недоступность справочника: задача выпуска
 * повторит чтение позже по своим правилам. Ответ, который повторять бессмысленно (нет записи, архив), выходит наружу
 * как есть, с первой же попытки.
 */
final class ReferenceRetries {

    private static final Logger log = LoggerFactory.getLogger(ReferenceRetries.class);

    private final RetryTemplate byId;
    private final RetryTemplate byExternalId;

    ReferenceRetries(RetryPolicy byId, RetryPolicy byExternalId) {
        this.byId = new RetryTemplate(byId);
        this.byExternalId = new RetryTemplate(byExternalId);
    }

    static ReferenceRetries of(ReferenceProperties.Retry settings) {
        return new ReferenceRetries(ReferenceRetryPolicies.byId(settings), ReferenceRetryPolicies.byExternalId(settings));
    }

    <T> T byId(Supplier<T> read) {
        return run(byId, read);
    }

    <T> T byExternalId(Supplier<T> read) {
        return run(byExternalId, read);
    }

    private static <T> T run(RetryTemplate template, Supplier<T> read) {
        try {
            return template.execute(read::get);
        }
        catch (RetryException outcome) {
            throw finalFailure(template.getRetryPolicy(), outcome);
        }
    }

    /** Повторяемая причина после исчерпания — недоступность; неповторяемая выходит наружу как есть. */
    private static RuntimeException finalFailure(RetryPolicy policy, RetryException outcome) {
        Throwable last = outcome.getCause();
        if (!policy.shouldRetry(last)) {
            return asRuntime(last);
        }
        log.warn("справочник недоступен, повторов сделано {}: {}", outcome.getRetryCount(), last.getMessage());
        return exhausted(last);
    }

    private static RuntimeException exhausted(Throwable last) {
        if (last instanceof ReferenceUnavailableException unavailable) {
            return unavailable;
        }
        return new ReferenceUnavailableException(Reason.POLICY_NOT_RECEIVED, last);
    }

    private static RuntimeException asRuntime(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            return runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(failure);
    }
}
