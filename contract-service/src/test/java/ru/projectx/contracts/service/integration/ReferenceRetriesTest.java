package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

/**
 * Повторы считаются и заканчиваются предсказуемо: временная недоступность повторяется заданное число раз и затем
 * выходит как недоступность, окончательный ответ выходит с первой попытки, а «не найден» повторяется только у поиска
 * полиса по идентификатору 1С:УСК. Пауза в этих тестах нулевая: её величину проверяет тест политики.
 */
class ReferenceRetriesTest {

    private static final int RETRIES = 2;
    private static final int ATTEMPTS = RETRIES + 1;

    private final ReferenceRetries retries = ReferenceRetries.of(new ReferenceProperties.Retry(RETRIES, Duration.ZERO));

    @Test
    void successfulRead_isReturnedFromTheFirstAttempt() {
        AtomicInteger attempts = new AtomicInteger();

        String read = retries.byId(() -> "ответ " + attempts.incrementAndGet());

        assertThat(read).isEqualTo("ответ 1");
        assertThat(attempts).hasValue(1);
    }

    @Test
    void unavailable_isRetriedUntilExhaustedThenReportedAsIs() {
        ReferenceUnavailableException unavailable = new ReferenceUnavailableException(Reason.SERVICE_UNAVAILABLE);
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retries.byId(() -> {
            attempts.incrementAndGet();
            throw unavailable;
        })).isSameAs(unavailable);
        assertThat(attempts).hasValue(ATTEMPTS);
    }

    @Test
    void unavailable_recoversWhenARetrySucceeds() {
        Supplier<String> read = answers(new ReferenceUnavailableException(Reason.TIMEOUT),
                new ReferenceUnavailableException(Reason.CONNECTION_FAILED), "готово");

        assertThat(retries.byId(read)).isEqualTo("готово");
    }

    @Test
    void notFoundById_isFinalAfterOneAttempt() {
        ReferenceNotFoundException notFound = new ReferenceNotFoundException(ReferenceKind.POLICY);
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retries.byId(() -> {
            attempts.incrementAndGet();
            throw notFound;
        })).isSameAs(notFound);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void policyNotFoundByExternalId_isRetriedThenReportedAsNotReceived() {
        ReferenceNotFoundException notFound = new ReferenceNotFoundException(ReferenceKind.POLICY);
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retries.byExternalId(() -> {
            attempts.incrementAndGet();
            throw notFound;
        })).isInstanceOfSatisfying(ReferenceUnavailableException.class,
                unavailable -> assertThat(unavailable.reason()).isEqualTo(Reason.POLICY_NOT_RECEIVED))
                .hasCause(notFound);
        assertThat(attempts).hasValue(ATTEMPTS);
    }

    @Test
    void policyByExternalId_isReturnedOnceItArrives() {
        Supplier<String> read = answers(new ReferenceNotFoundException(ReferenceKind.POLICY), "полис");

        assertThat(retries.byExternalId(read)).isEqualTo("полис");
    }

    /** По идентификатору 1С ищут только полис; чужой «не найден» там — окончательный ответ. */
    @Test
    void otherNotFoundByExternalId_isFinalAfterOneAttempt() {
        ReferenceNotFoundException notFound = new ReferenceNotFoundException(ReferenceKind.PROGRAM);
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retries.byExternalId(() -> {
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

        assertThatThrownBy(() -> retries.byId(read)).isSameAs(archived);
        assertThatThrownBy(() -> retries.byExternalId(read)).isSameAs(archived);
        assertThat(attempts).hasValue(2);
    }

    @Test
    void unexpectedRuntimeFailure_isNotRetriedAndPassesThrough() {
        IllegalStateException failure = new IllegalStateException("сбой");
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retries.byId(() -> {
            attempts.incrementAndGet();
            throw failure;
        })).isSameAs(failure);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void zeroRetries_meansASingleAttempt() {
        ReferenceRetries single = ReferenceRetries.of(new ReferenceProperties.Retry(0, Duration.ZERO));
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> single.byExternalId(() -> {
            attempts.incrementAndGet();
            throw new ReferenceNotFoundException(ReferenceKind.POLICY);
        })).isInstanceOf(ReferenceUnavailableException.class);
        assertThat(attempts).hasValue(1);
    }

    /** Ответы по порядку: исключения бросаются, последнее значение возвращается. */
    private static Supplier<String> answers(Object... scripted) {
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
