package ru.projectx.contracts.service.integration;

import java.util.function.Predicate;
import org.springframework.core.retry.RetryPolicy;
import ru.projectx.contracts.config.ReferenceProperties;
import ru.projectx.contracts.exception.ReferenceKind;
import ru.projectx.contracts.exception.ReferenceNotFoundException;
import ru.projectx.contracts.exception.ReferenceUnavailableException;

/**
 * Что повторяется при чтении справочника. Временная недоступность повторяется всегда. «Не найден» повторяется только
 * у поиска полиса по идентификатору 1С:УСК: о выпущенном полисе 1С сообщает сразу, а до справочника он может ещё идти.
 * Пауза между попытками постоянная, попыток столько, сколько задано в настройках.
 */
final class ReferenceRetryPolicies {

    private ReferenceRetryPolicies() {
    }

    static RetryPolicy byId(ReferenceProperties.Retry settings) {
        return policy(settings, ReferenceRetryPolicies::unavailable);
    }

    static RetryPolicy byExternalId(ReferenceProperties.Retry settings) {
        return policy(settings, failure -> unavailable(failure) || policyNotReceivedYet(failure));
    }

    private static RetryPolicy policy(ReferenceProperties.Retry settings, Predicate<Throwable> retryable) {
        return RetryPolicy.builder()
                .predicate(retryable)
                .maxRetries(settings.maxRetries())
                .delay(settings.delay())
                .build();
    }

    private static boolean unavailable(Throwable failure) {
        return failure instanceof ReferenceUnavailableException;
    }

    private static boolean policyNotReceivedYet(Throwable failure) {
        return failure instanceof ReferenceNotFoundException notFound && notFound.kind() == ReferenceKind.POLICY;
    }
}
