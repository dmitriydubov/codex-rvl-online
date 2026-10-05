package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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

/**
 * Защищает решение о судьбе сообщения 1С: что записывается при приёме, при успехе, при пропуске и при
 * временной или постоянной ошибке.
 *
 * <p>Временная ошибка должна двигать сообщение по лестнице задержек, а исчерпанная лестница — завершать его
 * отказом с номером попытки. Если лестница не кончается, сломанное сообщение повторяется вечно. Если она
 * кончается раньше, сообщение теряется после первого же сбоя базы. Работа с настоящей базой проверяется
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
    private static final String ERROR = "база недоступна";

    @Mock
    private InboundMessageRepository repository;

    @Test
    void store_savesTheMessageAsNewWithTheMomentOfReceipt() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InboundMessage stored = service().store(MESSAGE_ID, OneCStream.MKB10, QUEUE, PAYLOAD);

        assertThat(stored.receipt()).isEqualTo(new InboundReceipt(MESSAGE_ID, OneCStream.MKB10, QUEUE, NOW));
        assertThat(stored.payload()).isEqualTo(PAYLOAD);
        assertThat(stored.outcome().status()).isEqualTo(InboundStatus.NEW);
    }

    @Test
    void processed_isStampedWithTheCurrentMoment() {
        InboundMessage message = existing();

        service().markProcessed(MESSAGE_ID);

        assertThat(message.outcome()).isEqualTo(new InboundOutcome(InboundStatus.PROCESSED, 0, null, null, NOW));
    }

    @Test
    void skipped_keepsTheReasonAndTheCurrentMoment() {
        InboundMessage message = existing();

        service().markSkipped(MESSAGE_ID, "программа не РВЛ");

        assertThat(message.outcome())
                .isEqualTo(new InboundOutcome(InboundStatus.SKIPPED, 0, null, "программа не РВЛ", NOW));
    }

    @Test
    void firstTemporaryFailure_schedulesTheFirstStepOfTheLadder() {
        InboundMessage message = existing();

        service().recordTemporaryFailure(MESSAGE_ID, ERROR);

        assertThat(message.outcome())
                .isEqualTo(new InboundOutcome(InboundStatus.RETRY, 1, NOW.plus(BACKOFF.get(0)), ERROR, null));
    }

    @Test
    void nextTemporaryFailure_takesTheNextStepOfTheLadder() {
        InboundMessage message = existing();
        message.markRetrying("первая ошибка", NOW);

        service().recordTemporaryFailure(MESSAGE_ID, ERROR);

        assertThat(message.outcome())
                .isEqualTo(new InboundOutcome(InboundStatus.RETRY, 2, NOW.plus(BACKOFF.get(1)), ERROR, null));
    }

    @Test
    void temporaryFailureAfterTheLastStep_failsTheMessageWithTheAttemptNumber() {
        InboundMessage message = existing();
        message.markRetrying("первая ошибка", NOW);
        message.markRetrying("вторая ошибка", NOW);

        service().recordTemporaryFailure(MESSAGE_ID, ERROR);

        assertThat(message.outcome()).isEqualTo(new InboundOutcome(
                InboundStatus.FAILED, 2, null, "лестница повторов исчерпана на попытке 3: " + ERROR, null));
    }

    @Test
    void permanentFailure_failsTheMessageAtOnce() {
        InboundMessage message = existing();

        service().recordPermanentFailure(MESSAGE_ID, "в конверте нет объекта data");

        assertThat(message.outcome())
                .isEqualTo(new InboundOutcome(InboundStatus.FAILED, 0, null, "в конверте нет объекта data", null));
    }

    @Test
    void outcomeOfAnUnknownMessage_isAnError() {
        when(repository.findById(MESSAGE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().markProcessed(MESSAGE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("нет строки сырья для сообщения " + MESSAGE_ID);
    }

    private InboundMessage existing() {
        InboundMessage message = InboundMessage.receive(
                new InboundReceipt(MESSAGE_ID, OneCStream.MKB10, QUEUE, RECEIVED_AT), PAYLOAD);
        when(repository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
        return message;
    }

    private InboundMessageService service() {
        return new InboundMessageService(
                repository, new InboundRetryProperties(BACKOFF), Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
