package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.util.backoff.BackOffExecution;
import ru.projectx.contracts.config.ReferenceProperties;
import ru.projectx.contracts.exception.ReferenceArchivedException;
import ru.projectx.contracts.exception.ReferenceKind;
import ru.projectx.contracts.exception.ReferenceNotFoundException;
import ru.projectx.contracts.exception.ReferenceRejectedException;
import ru.projectx.contracts.exception.ReferenceUnavailableException;
import ru.projectx.contracts.exception.ReferenceUnavailableException.Reason;

/**
 * Политика повторов берёт число попыток и паузу из настроек и повторяет ровно то, что имеет смысл повторять.
 * Пауза проверяется по расписанию политики, без ожидания.
 */
class ReferenceRetryPoliciesTest {

    private static final ReferenceProperties.Retry SETTINGS =
            new ReferenceProperties.Retry(3, Duration.ofMillis(1500));

    private final RetryPolicy byId = ReferenceRetryPolicies.byId(SETTINGS);
    private final RetryPolicy byExternalId = ReferenceRetryPolicies.byExternalId(SETTINGS);

    @ParameterizedTest
    @ValueSource(strings = {"SERVICE_UNAVAILABLE", "TIMEOUT", "CONNECTION_FAILED", "UNEXPECTED_RESPONSE"})
    void unavailable_isRetriedInBothModes(Reason reason) {
        ReferenceUnavailableException unavailable = new ReferenceUnavailableException(reason);

        assertThat(byId.shouldRetry(unavailable)).isTrue();
        assertThat(byExternalId.shouldRetry(unavailable)).isTrue();
    }

    @Test
    void policyNotFound_isRetriedOnlyByExternalId() {
        ReferenceNotFoundException policyNotFound = new ReferenceNotFoundException(ReferenceKind.POLICY);

        assertThat(byId.shouldRetry(policyNotFound)).isFalse();
        assertThat(byExternalId.shouldRetry(policyNotFound)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROGRAM", "INSURANCE_CONTRACT"})
    void otherNotFound_isNeverRetried(ReferenceKind kind) {
        ReferenceNotFoundException notFound = new ReferenceNotFoundException(kind);

        assertThat(byId.shouldRetry(notFound)).isFalse();
        assertThat(byExternalId.shouldRetry(notFound)).isFalse();
    }

    @Test
    void archivedRejectedAndUnexpectedFailures_areNeverRetried() {
        ReferenceArchivedException archived = new ReferenceArchivedException(ReferenceKind.PROGRAM,
                UUID.fromString("7d1f1a26-3a0f-4a4e-8d7f-5a2b3c4d5e6f"));
        ReferenceRejectedException rejected = new ReferenceRejectedException(400, "VALIDATION_ERROR");
        IllegalStateException unexpected = new IllegalStateException("сбой");

        assertThat(byId.shouldRetry(archived)).isFalse();
        assertThat(byExternalId.shouldRetry(archived)).isFalse();
        assertThat(byId.shouldRetry(rejected)).isFalse();
        assertThat(byExternalId.shouldRetry(rejected)).isFalse();
        assertThat(byId.shouldRetry(unexpected)).isFalse();
        assertThat(byExternalId.shouldRetry(unexpected)).isFalse();
    }

    /** Три повтора через полторы секунды каждый, потом стоп: пауза не растёт и не сокращается. */
    @Test
    void backOff_pausesTheConfiguredDelayExactlyMaxRetriesTimes() {
        BackOffExecution schedule = byId.getBackOff().start();

        long[] pauses = LongStream.range(0, SETTINGS.maxRetries()).map(attempt -> schedule.nextBackOff()).toArray();

        assertThat(pauses).containsOnly(SETTINGS.delay().toMillis());
        assertThat(schedule.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void backOff_byExternalId_followsTheSameSettings() {
        BackOffExecution schedule = byExternalId.getBackOff().start();

        assertThat(schedule.nextBackOff()).isEqualTo(SETTINGS.delay().toMillis());
    }

    @Test
    void zeroRetries_stopsBeforeTheFirstPause() {
        RetryPolicy none = ReferenceRetryPolicies.byId(new ReferenceProperties.Retry(0, Duration.ofSeconds(1)));

        assertThat(none.getBackOff().start().nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void policy_hasNoOverallTimeout() {
        assertThat(byId.getTimeout()).isZero();
    }
}
