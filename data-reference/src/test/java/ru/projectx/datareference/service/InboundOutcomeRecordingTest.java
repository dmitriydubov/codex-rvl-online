package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.config.InboundRetryProperties;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundOutcome;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.metrics.InboundTransitionMetrics;

/**
 * Защищает решение о судьбе сообщения 1С: что записывается при приёме, при успехе, при пропуске и при
 * временной или постоянной ошибке.
 *
 * <p>После временной ошибки следующая попытка назначается через задержку из настроенного списка повторов. Когда
 * список исчерпан, сообщение завершается отказом, и в тексте отказа указан номер попытки. Если повторы не
 * кончаются, сломанное сообщение обрабатывается вечно. Если они кончаются раньше срока, сообщение теряется после
 * первого же сбоя базы. Работа с настоящей базой проверяется
 * отдельным тестом с PostgreSQL; здесь только решения сервиса.
 */
@ExtendWith(MockitoExtension.class)
class InboundOutcomeRecordingTest {

    private static final UUID MESSAGE_ID = UUID.fromString("7d2b1f0e-0000-4000-8000-0000000000aa");
    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";
    private static final String PAYLOAD = "{\"data\":{}}";
    private static final Instant RECEIVED_AT = Instant.parse("2026-03-14T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-03-14T09:15:00Z");
    private static final List<Duration> BACKOFF = List.of(Duration.ofSeconds(30), Duration.ofMinutes(5));
    private static final ParseFailure.Temporary LOCK_TIMEOUT = new ParseFailure.Temporary(
            "временный отказ базы данных: CannotAcquireLockException, SQLSTATE 55P03",
            "CannotAcquireLockException", "55P03");
    private static final String ERROR = LOCK_TIMEOUT.reason();

    @Mock
    private InboundMessageRepository repository;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    @Test
    void store_savesTheMessageAsNewWithTheMomentOfReceipt() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InboundMessage stored = service().store(MESSAGE_ID, OneCStream.MKB10, QUEUE, PAYLOAD);

        assertThat(stored.receipt()).isEqualTo(new InboundReceipt(MESSAGE_ID, OneCStream.MKB10, QUEUE, NOW));
        assertThat(stored.payload()).isEqualTo(PAYLOAD);
        assertThat(stored.outcome().status()).isEqualTo(InboundStatus.NEW);
        assertThat(transitions()).isEmpty();
    }

    @Test
    void processed_isStampedWithTheCurrentMoment() {
        InboundMessage message = existing();

        service().markProcessed(MESSAGE_ID);

        assertThat(message.outcome()).isEqualTo(new InboundOutcome(InboundStatus.PROCESSED, 0, null, null, NOW));
        assertThat(transitions()).containsExactly("PROCESSED");
    }

    @Test
    void skipped_keepsTheReasonAndTheCurrentMoment() {
        InboundMessage message = existing();

        service().markSkipped(MESSAGE_ID, "программа не РВЛ");

        assertThat(message.outcome())
                .isEqualTo(new InboundOutcome(InboundStatus.SKIPPED, 0, null, "программа не РВЛ", NOW));
        assertThat(transitions()).containsExactly("SKIPPED");
    }

    @Test
    void firstTemporaryFailure_schedulesTheFirstStepOfTheLadder() {
        InboundMessage message = existing();

        service().recordTemporaryFailure(MESSAGE_ID, LOCK_TIMEOUT);

        assertThat(message.outcome())
                .isEqualTo(new InboundOutcome(InboundStatus.RETRY, 1, NOW.plus(BACKOFF.get(0)), ERROR, null));
        assertThat(transitions()).containsExactly("RETRY");
    }

    @Test
    void nextTemporaryFailure_takesTheNextStepOfTheLadder() {
        InboundMessage message = existing();
        message.markRetrying("первая ошибка", NOW);

        service().recordTemporaryFailure(MESSAGE_ID, LOCK_TIMEOUT);

        assertThat(message.outcome())
                .isEqualTo(new InboundOutcome(InboundStatus.RETRY, 2, NOW.plus(BACKOFF.get(1)), ERROR, null));
    }

    @Test
    void temporaryFailureAfterTheLastStep_failsTheMessageWithTheAttemptNumber() {
        InboundMessage message = existing();
        message.markRetrying("первая ошибка", NOW);
        message.markRetrying("вторая ошибка", NOW);

        service().recordTemporaryFailure(MESSAGE_ID, LOCK_TIMEOUT);

        assertThat(message.outcome()).isEqualTo(new InboundOutcome(
                InboundStatus.FAILED, 2, null, "повторы исчерпаны на попытке 3: " + ERROR, null));
        assertThat(transitions()).containsExactly("FAILED");
    }

    @Test
    void permanentFailure_failsTheMessageAtOnce() {
        InboundMessage message = existing();

        service().recordPermanentFailure(MESSAGE_ID,
                new ParseFailure.Permanent("в конверте нет объекта data", "OneCFieldException", "нет"));

        assertThat(message.outcome())
                .isEqualTo(new InboundOutcome(InboundStatus.FAILED, 0, null, "в конверте нет объекта data", null));
        assertThat(transitions()).containsExactly("FAILED");
    }

    @Test
    void transferParts_areReadByTransferId() {
        UUID transfer = UUID.fromString("7d2b1f0e-0000-4000-8000-0000000000cc");
        List<InboundMessage> parts = List.of(InboundMessage.receive(
                new InboundReceipt(MESSAGE_ID, OneCStream.PRICE_LISTS, QUEUE, RECEIVED_AT), PAYLOAD));
        when(repository.findByCorrelationId(transfer)).thenReturn(parts);

        assertThat(service().transferParts(transfer)).isSameAs(parts);
    }

    /** Статус части передачи ставит сборщик прайсов; сервис сохраняет строку и считает переход. */
    @Test
    void savedTransferPart_isStoredAndCounted() {
        InboundMessage part = InboundMessage.receive(
                new InboundReceipt(MESSAGE_ID, OneCStream.MKB10, QUEUE, RECEIVED_AT), PAYLOAD);
        part.markFailed("счётчики передачи не сошлись");

        service().saveTransferPart(part);

        verify(repository).save(part);
        assertThat(transitions()).containsExactly("FAILED");
    }

    @Test
    void outcomeOfAnUnknownMessage_isAnError() {
        when(repository.findById(MESSAGE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().markProcessed(MESSAGE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("нет строки сырья для сообщения " + MESSAGE_ID);
    }

    /** Посчитанные переходы потока сообщения, по одному на переход: здесь транзакции нет, счёт идёт сразу. */
    private List<String> transitions() {
        return meters.find(InboundTransitionMetrics.TRANSITIONS).tag("stream", "MKB10").counters().stream()
                .flatMap(counter -> Collections.nCopies((int) counter.count(), counter.getId().getTag("status")).stream())
                .toList();
    }

    private InboundMessage existing() {
        InboundMessage message = InboundMessage.receive(
                new InboundReceipt(MESSAGE_ID, OneCStream.MKB10, QUEUE, RECEIVED_AT), PAYLOAD);
        when(repository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
        return message;
    }

    private InboundMessageService service() {
        return new InboundMessageService(
                repository, new InboundRetryProperties(BACKOFF), Clock.fixed(NOW, ZoneOffset.UTC),
                new InboundTransitionMetrics(meters), new FinalRejectionJournal());
    }
}
