package ru.projectx.contracts.service.integration;

import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import ru.projectx.contracts.config.ReferenceProperties;
import ru.projectx.contracts.exception.ReferenceKind;
import ru.projectx.contracts.exception.ReferenceUnavailableException;
import ru.projectx.contracts.exception.ReferenceUnavailableException.Reason;

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

    <T> T byId(ReferenceKind kind, UUID id, Supplier<T> read) {
        return run(byId, kind, id, read);
    }

    <T> T byExternalId(ReferenceKind kind, UUID externalId, Supplier<T> read) {
        return run(byExternalId, kind, externalId, read);
    }

    private static <T> T run(RetryTemplate template, ReferenceKind kind, UUID key, Supplier<T> read) {
        try {
            return template.execute(read::get);
        }
        catch (RetryException outcome) {
            throw finalFailure(template.getRetryPolicy(), outcome, kind, key);
        }
    }

    private static RuntimeException finalFailure(
            RetryPolicy policy, RetryException outcome, ReferenceKind kind, UUID key) {
        Throwable last = outcome.getCause();
        if (last instanceof Error error) {
            throw error;
        }
        if (!policy.shouldRetry(last)) {
            return asRuntime(last);
        }
        log.warn("справочник недоступен: {} {}, повторов {}, причина: {}",
                kind, key, outcome.getRetryCount(), last.getMessage());
        return exhausted(last);
    }

    private static RuntimeException exhausted(Throwable last) {
        if (last instanceof ReferenceUnavailableException unavailable) {
            return unavailable;
        }
        return new ReferenceUnavailableException(Reason.POLICY_NOT_RECEIVED, last);
    }

    private static RuntimeException asRuntime(Throwable failure) {
        return failure instanceof RuntimeException runtime ? runtime : new IllegalStateException(failure);
    }
}
