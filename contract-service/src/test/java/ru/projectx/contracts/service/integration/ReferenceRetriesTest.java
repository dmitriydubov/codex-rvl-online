package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.config.ReferenceProperties;
import ru.projectx.contracts.exception.ReferenceArchivedException;
import ru.projectx.contracts.exception.ReferenceKind;
import ru.projectx.contracts.exception.ReferenceNotFoundException;
import ru.projectx.contracts.exception.ReferenceUnavailableException;
import ru.projectx.contracts.exception.ReferenceUnavailableException.Reason;

class ReferenceRetriesTest {

    private static final int RETRIES = 2;
    private static final int ATTEMPTS = RETRIES + 1;
    private static final ReferenceKind KIND = ReferenceKind.POLICY;
    private static final UUID KEY = UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f");

    private final ReferenceRetries retries = ReferenceRetries.of(new ReferenceProperties.Retry(RETRIES, Duration.ZERO));

    @Test
    void successfulRead_isReturnedFromTheFirstAttempt() {
        AtomicInteger attempts = new AtomicInteger();

        String read = retries.byId(KIND, KEY, () -> "ответ " + attempts.incrementAndGet());

        assertThat(read).isEqualTo("ответ 1");
        assertThat(attempts).hasValue(1);
    }

    @Test
    void unavailable_isRetriedUntilExhaustedThenReportedAsIs() {
        ReferenceUnavailableException unavailable = new ReferenceUnavailableException(Reason.SERVICE_UNAVAILABLE);
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retries.byId(KIND, KEY, () -> {
            attempts.incrementAndGet();
            throw unavailable;
        })).isSameAs(unavailable);
        assertThat(attempts).hasValue(ATTEMPTS);
    }

    @Test
    void unavailable_recoversWhenARetrySucceeds() {
        Supplier<String> read = attemptsInOrder(new ReferenceUnavailableException(Reason.TIMEOUT),
                new ReferenceUnavailableException(Reason.CONNECTION_FAILED), "готово");

        assertThat(retries.byId(KIND, KEY, read)).isEqualTo("готово");
    }

    @Test
    void notFoundById_isFinalAfterOneAttempt() {
        ReferenceNotFoundException notFound = new ReferenceNotFoundException(ReferenceKind.POLICY);
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retries.byId(KIND, KEY, () -> {
            attempts.incrementAndGet();
            throw notFound;
        })).isSameAs(notFound);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void policyNotFoundByExternalId_isRetriedThenReportedAsNotReceived() {
        ReferenceNotFoundException notFound = new ReferenceNotFoundException(ReferenceKind.POLICY);
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retries.byExternalId(KIND, KEY, () -> {
            attempts.incrementAndGet();
            throw notFound;
        })).isInstanceOfSatisfying(ReferenceUnavailableException.class,
                unavailable -> assertThat(unavailable.reason()).isEqualTo(Reason.POLICY_NOT_RECEIVED))
                .hasCause(notFound);
        assertThat(attempts).hasValue(ATTEMPTS);
    }

    @Test
    void policyByExternalId_isReturnedOnceItArrives() {
        Supplier<String> read = attemptsInOrder(new ReferenceNotFoundException(ReferenceKind.POLICY), "полис");

        assertThat(retries.byExternalId(KIND, KEY, read)).isEqualTo("полис");
    }

    @Test
    void otherNotFoundByExternalId_isFinalAfterOneAttempt() {
        ReferenceNotFoundException notFound = new ReferenceNotFoundException(ReferenceKind.PROGRAM);
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retries.byExternalId(KIND, KEY, () -> {
            attempts.incrementAndGet();
            throw notFound;
        })).isSameAs(notFound);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void archived_isFinalInBothModes() {
        ReferenceArchivedException archived = new ReferenceArchivedException(ReferenceKind.POLICY,
                UUID.fromString("1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f"));
        AtomicInteger attempts = new AtomicInteger();
        Supplier<Object> read = () -> {
            attempts.incrementAndGet();
            throw archived;
        };

        assertThatThrownBy(() -> retries.byId(KIND, KEY, read)).isSameAs(archived);
        assertThatThrownBy(() -> retries.byExternalId(KIND, KEY, read)).isSameAs(archived);
        assertThat(attempts).hasValue(2);
    }

    @Test
    void unexpectedRuntimeFailure_isNotRetriedAndPassesThrough() {
        IllegalStateException failure = new IllegalStateException("сбой");
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retries.byId(KIND, KEY, () -> {
            attempts.incrementAndGet();
            throw failure;
        })).isSameAs(failure);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void jvmError_isNotRetriedAndPassesThroughUnwrapped() {
        Error fatal = new Error("ошибка виртуальной машины");
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retries.byExternalId(KIND, KEY, () -> {
            attempts.incrementAndGet();
            throw fatal;
        })).isSameAs(fatal);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void checkedFailure_isWrappedInIllegalStateExceptionWithoutRetry() {
        IOException checked = new IOException("обрыв");
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retries.byId(KIND, KEY, () -> {
            attempts.incrementAndGet();
            return throwUndeclared(checked);
        })).isExactlyInstanceOf(IllegalStateException.class).hasCause(checked);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void zeroRetries_meansASingleAttempt() {
        ReferenceRetries single = ReferenceRetries.of(new ReferenceProperties.Retry(0, Duration.ZERO));
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> single.byExternalId(KIND, KEY, () -> {
            attempts.incrementAndGet();
            throw new ReferenceNotFoundException(ReferenceKind.POLICY);
        })).isInstanceOf(ReferenceUnavailableException.class);
        assertThat(attempts).hasValue(1);
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T throwUndeclared(Throwable failure) throws E {
        throw (E) failure;
    }

    private static Supplier<String> attemptsInOrder(Object... scripted) {
        Deque<Object> queue = new ArrayDeque<>(List.of(scripted));
        return () -> {
            Object next = queue.poll();
            if (next instanceof RuntimeException failure) {
                throw failure;
            }
            return (String) next;
        };
    }
}
