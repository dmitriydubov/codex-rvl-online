package ru.projectx.datareference.service.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import ru.projectx.datareference.entity.AwaitedParent;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundOutcome;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.ParentKind;
import ru.projectx.datareference.entity.ParentWait;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.service.metrics.InboundTransitionMetrics;

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
    private static final AwaitedParent PARENT =
            new AwaitedParent(ParentKind.CONTRAGENT, UUID.fromString("6d0e4a7b-2c95-4f18-8b3d-e9a1c7f50d62"));

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

        assertThat(message.outcome()).isEqualTo(new InboundOutcome(InboundStatus.PROCESSED, 0, null, null, NOW, null));
        assertThat(transitions()).containsExactly("PROCESSED");
    }

    @Test
    void skipped_keepsTheReasonAndTheCurrentMoment() {
        InboundMessage message = existing();

        service().markSkipped(MESSAGE_ID, "программа не РВЛ");

        assertThat(message.outcome())
                .isEqualTo(new InboundOutcome(InboundStatus.SKIPPED, 0, null, "программа не РВЛ", NOW, null));
        assertThat(transitions()).containsExactly("SKIPPED");
    }

    @Test
    void awaitingParent_remembersTheParentTheRecordAndTheCurrentMoment() {
        InboundMessage message = existing();
        UUID record = UUID.fromString("5f14a8d2-c3e7-4b90-a1d6-9e2b7c4f0a83");

        service().markAwaitingParent(MESSAGE_ID, PARENT, record);

        assertThat(message.outcome()).isEqualTo(new InboundOutcome(InboundStatus.AWAITING_PARENT, 0, null,
                PARENT.waitingReason(), null, new ParentWait(PARENT, NOW, record)));
        assertThat(transitions()).containsExactly("AWAITING_PARENT");
    }

    @Test
    void temporaryFailureOfAWaitingMessage_schedulesARetry() {
        InboundMessage message = existing();
        message.markAwaitingParent(PARENT, null, RECEIVED_AT);

        assertThat(service().recordTemporaryFailure(MESSAGE_ID, unchanged(), LOCK_TIMEOUT))
                .isEqualTo(InboundStatus.RETRY);
        assertThat(message.outcome().nextRetryAt()).isEqualTo(NOW.plus(BACKOFF.get(0)));
    }

    @Test
    void permanentFailureOfAWaitingMessage_failsIt() {
        InboundMessage message = existing();
        message.markAwaitingParent(PARENT, null, RECEIVED_AT);

        assertThat(service().recordPermanentFailure(MESSAGE_ID, unchanged(),
                new ParseFailure.Permanent("в договоре нет даты начала", "OneCFieldException", "нет")))
                .isEqualTo(InboundStatus.FAILED);
        assertThat(message.outcome().lastError()).isEqualTo("в договоре нет даты начала");
    }

    @Test
    void firstTemporaryFailure_schedulesTheFirstStepOfTheLadder() {
        InboundMessage message = existing();

        service().recordTemporaryFailure(MESSAGE_ID, unchanged(), LOCK_TIMEOUT);

        assertThat(message.outcome())
                .isEqualTo(new InboundOutcome(InboundStatus.RETRY, 1, NOW.plus(BACKOFF.get(0)), ERROR, null, null));
        assertThat(transitions()).containsExactly("RETRY");
    }

    @Test
    void nextTemporaryFailure_takesTheNextStepOfTheLadder() {
        InboundMessage message = existing();
        message.markRetrying("первая ошибка", NOW);

        service().recordTemporaryFailure(MESSAGE_ID, unchanged(), LOCK_TIMEOUT);

        assertThat(message.outcome())
                .isEqualTo(new InboundOutcome(InboundStatus.RETRY, 2, NOW.plus(BACKOFF.get(1)), ERROR, null, null));
    }

    @Test
    void temporaryFailureAfterTheLastStep_failsTheMessageWithTheAttemptNumber() {
        InboundMessage message = existing();
        message.markRetrying("первая ошибка", NOW);
        message.markRetrying("вторая ошибка", NOW);

        service().recordTemporaryFailure(MESSAGE_ID, unchanged(), LOCK_TIMEOUT);

        assertThat(message.outcome()).isEqualTo(new InboundOutcome(
                InboundStatus.FAILED, 2, null, "повторы исчерпаны на попытке 3: " + ERROR, null, null));
        assertThat(transitions()).containsExactly("FAILED");
    }

    @Test
    void permanentFailure_failsTheMessageAtOnce() {
        InboundMessage message = existing();

        service().recordPermanentFailure(MESSAGE_ID, unchanged(),
                new ParseFailure.Permanent("в конверте нет объекта data", "OneCFieldException", "нет"));

        assertThat(message.outcome())
                .isEqualTo(new InboundOutcome(InboundStatus.FAILED, 0, null, "в конверте нет объекта data", null, null));
        assertThat(transitions()).containsExactly("FAILED");
    }

    @Test
    void permanentFailureInSharedScope_failsEveryMessageTheScopeNames_inOneRecording() {
        InboundMessage message = existing();
        InboundMessage sibling = InboundMessage.receive(
                new InboundReceipt(UUID.randomUUID(), OneCStream.MKB10, QUEUE, RECEIVED_AT), PAYLOAD);
        RejectionScope scope = scopeFailing(sibling);

        InboundStatus status = service(scope).recordPermanentFailure(MESSAGE_ID, unchanged(),
                new ParseFailure.Permanent("в конверте нет объекта data", "OneCFieldException", "нет"));

        assertThat(status).isEqualTo(InboundStatus.FAILED);
        verify(scope).reject(message, "в конверте нет объекта data");
        assertThat(sibling.outcome().lastError()).isEqualTo("задет: в конверте нет объекта data");
        assertThat(transitions()).containsExactly("FAILED", "FAILED");
    }

    @Test
    void exhaustedLadderInSharedScope_failsEveryMessageTheScopeNames() {
        InboundMessage message = existing();
        message.markRetrying("первая ошибка", NOW);
        message.markRetrying("вторая ошибка", NOW);
        InboundMessage sibling = InboundMessage.receive(
                new InboundReceipt(UUID.randomUUID(), OneCStream.MKB10, QUEUE, RECEIVED_AT), PAYLOAD);

        InboundStatus status =
                service(scopeFailing(sibling)).recordTemporaryFailure(MESSAGE_ID, unchanged(), LOCK_TIMEOUT);

        assertThat(status).isEqualTo(InboundStatus.FAILED);
        assertThat(message.outcome().lastError()).isEqualTo("повторы исчерпаны на попытке 3: " + ERROR);
        assertThat(sibling.outcome().lastError()).isEqualTo("задет: повторы исчерпаны на попытке 3: " + ERROR);
        assertThat(transitions()).containsExactly("FAILED", "FAILED");
    }

    @Test
    void retryInSharedScope_touchesOnlyTheMessage() {
        existing();
        RejectionScope scope = mock(RejectionScope.class);
        when(scope.stream()).thenReturn(OneCStream.MKB10);

        assertThat(service(scope).recordTemporaryFailure(MESSAGE_ID, unchanged(), LOCK_TIMEOUT))
                .isEqualTo(InboundStatus.RETRY);
        verify(scope, never()).reject(any(), any());
        assertThat(transitions()).containsExactly("RETRY");
    }

    @Test
    void permanentFailureOfOtherStream_isNotShared() {
        InboundMessage message = existing();
        RejectionScope scope = mock(RejectionScope.class);
        when(scope.stream()).thenReturn(OneCStream.PRICE_LISTS);

        service(scope).recordPermanentFailure(MESSAGE_ID, unchanged(),
                new ParseFailure.Permanent("в конверте нет объекта data", "OneCFieldException", "нет"));

        assertThat(message.outcome().lastError()).isEqualTo("в конверте нет объекта data");
        verify(scope, never()).reject(any(), any());
    }

    @Test
    void transferParts_areReadByTransferId() {
        UUID transfer = UUID.fromString("7d2b1f0e-0000-4000-8000-0000000000cc");
        List<InboundMessage> parts = List.of(InboundMessage.receive(
                new InboundReceipt(MESSAGE_ID, OneCStream.PRICE_LISTS, QUEUE, RECEIVED_AT), PAYLOAD));
        when(repository.findByCorrelationId(transfer)).thenReturn(parts);

        assertThat(service().transferParts(transfer)).isSameAs(parts);
    }

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

    private List<String> transitions() {
        return meters.find(InboundTransitionMetrics.TRANSITIONS).tag("stream", "MKB10").counters().stream()
                .flatMap(counter -> Collections.nCopies((int) counter.count(), counter.getId().getTag("status")).stream())
                .toList();
    }

    private InboundOutcome unchanged() {
        return repository.findById(MESSAGE_ID).orElseThrow().outcome();
    }

    private InboundMessage existing() {
        InboundMessage message = InboundMessage.receive(
                new InboundReceipt(MESSAGE_ID, OneCStream.MKB10, QUEUE, RECEIVED_AT), PAYLOAD);
        when(repository.findById(MESSAGE_ID)).thenReturn(Optional.of(message));
        return message;
    }

    private static RejectionScope scopeFailing(InboundMessage sibling) {
        RejectionScope scope = mock(RejectionScope.class);
        when(scope.stream()).thenReturn(OneCStream.MKB10);
        when(scope.reject(any(), any())).thenAnswer(invocation -> {
            InboundMessage message = invocation.getArgument(0);
            String reason = invocation.getArgument(1);
            message.markFailed(reason);
            sibling.markFailed("задет: " + reason);
            return List.of(message, sibling);
        });
        return scope;
    }

    private InboundMessageService service(RejectionScope... scopes) {
        return new InboundMessageService(
                repository, new InboundRetryProperties(BACKOFF), Clock.fixed(NOW, ZoneOffset.UTC),
                new InboundTransitionMetrics(meters), new FinalRejectionJournal(), List.of(scopes));
    }
}
