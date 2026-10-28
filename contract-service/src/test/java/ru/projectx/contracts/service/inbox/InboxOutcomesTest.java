package ru.projectx.contracts.service.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.config.journal.InboxProperties;
import ru.projectx.contracts.entity.inbox.InboxMessage;
import ru.projectx.contracts.entity.inbox.InboxReceipt;
import ru.projectx.contracts.entity.inbox.InboxStatus;
import ru.projectx.contracts.repository.InboxMessageRepository;
import ru.projectx.contracts.service.leadership.LeadershipFence;

class InboxOutcomesTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final InboxReceipt RECEIPT = new InboxReceipt(
            UUID.fromString("b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11"),
            UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803"),
            "rvl.contract-task.upserted", "ContractTaskUpserted", 1);
    private static final InboxFailure.Transient LOCKED = InboxFailure.Transient.database("55P03");

    private final InboxMessageRepository messages = mock();
    private final InboxMetrics metrics = mock();
    private final LeadershipFence fence = mock();
    private final InboxOutcomes outcomes = new InboxOutcomes(messages, properties(), metrics, fence,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final InboxMessage message = InboxMessage.receive(RECEIPT, "{}", NOW);

    @BeforeEach
    void messageIsStored() {
        given(messages.findById(message.id())).willReturn(Optional.of(message));
    }

    @Test
    void firstTransientFailure_schedulesRetryAfterFirstPause() {
        outcomes.recordFailure(message.id(), LOCKED);

        assertThat(message.status()).isEqualTo(InboxStatus.NEW);
        assertThat(message.attempts()).isEqualTo(1);
        assertThat(message.errorMessage()).contains("временный отказ базы данных, SQLSTATE 55P03");
        assertThat(message).hasFieldOrPropertyWithValue("nextAttemptAt", NOW.plus(Duration.ofMinutes(1)));
        verify(metrics, never()).countFailed(anyString());
    }

    @Test
    void thirdTransientFailure_schedulesRetryAfterLastPause() {
        message.markRetry("отказ", NOW);
        message.markRetry("отказ", NOW);

        outcomes.recordFailure(message.id(), LOCKED);

        assertThat(message.attempts()).isEqualTo(3);
        assertThat(message).hasFieldOrPropertyWithValue("nextAttemptAt", NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void fourthTransientFailure_rejectsMessageAndCountsIt() {
        message.markRetry("отказ", NOW);
        message.markRetry("отказ", NOW);
        message.markRetry("отказ", NOW);

        outcomes.recordFailure(message.id(), LOCKED);

        assertThat(message.status()).isEqualTo(InboxStatus.FAILED);
        assertThat(message.attempts()).isEqualTo(4);
        assertThat(message.errorMessage())
                .contains("повторы исчерпаны на попытке 4: временный отказ базы данных, SQLSTATE 55P03");
        verify(metrics).countFailed("ContractTaskUpserted");
    }

    @Test
    void permanentFailure_rejectsMessageAtFirstAttemptWithItsReason() {
        outcomes.recordFailure(message.id(), new InboxFailure.Permanent("нет обязательного поля data.date_from", "нет"));

        assertThat(message.status()).isEqualTo(InboxStatus.FAILED);
        assertThat(message.attempts()).isEqualTo(1);
        assertThat(message.errorMessage()).contains("нет обязательного поля data.date_from");
        verify(metrics).countFailed("ContractTaskUpserted");
    }

    @Test
    void failureRecordedAfterAnotherPassFinishedTheMessage_changesNothing() {
        message.markProcessed(NOW);

        outcomes.recordFailure(message.id(), LOCKED);

        assertThat(message.status()).isEqualTo(InboxStatus.PROCESSED);
        assertThat(message.attempts()).isZero();
        verify(metrics, never()).countFailed(anyString());
    }

    @Test
    void unknownId_isIllegalState() {
        UUID unknown = UUID.fromString("00000000-0000-4000-8000-000000000001");
        given(messages.findById(unknown)).willReturn(Optional.empty());

        assertThatThrownBy(() -> outcomes.recordFailure(unknown, LOCKED)).isInstanceOf(IllegalStateException.class);
    }

    private static InboxProperties properties() {
        return new InboxProperties(new InboxProperties.Processing(Duration.ofSeconds(10), 100),
                new InboxProperties.Retry(List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15))),
                new InboxProperties.Retention(Duration.ofDays(30), Duration.ofHours(1), 500),
                new InboxProperties.Pause(Duration.ofSeconds(10), Duration.ofSeconds(30)), Duration.ofSeconds(5));
    }

    @Test
    void lostLeaderLock_failsTheRecordingAfterTheMessageIsReadAndBeforeItsOutcomeIsWritten() {
        org.mockito.BDDMockito.willThrow(new ru.projectx.contracts.service.leadership.LeadershipLostException("0b5f3c1e"))
                .given(fence).holdUntilCommit();

        assertThatThrownBy(() -> outcomes.recordFailure(message.id(), new InboxFailure.Permanent("отказ", "нет")))
                .isInstanceOf(ru.projectx.contracts.service.leadership.LeadershipLostException.class);

        verify(messages).findById(message.id());
        assertThat(message.status()).isEqualTo(InboxStatus.NEW);
        assertThat(message.attempts()).isZero();
    }

    @Test
    void outcomeAlreadyRecorded_isLeftWithoutTheLeaderLockRowBeingHeld() {
        message.markProcessed(NOW.minusSeconds(1));

        outcomes.recordFailure(message.id(), LOCKED);

        org.mockito.Mockito.verifyNoInteractions(fence);
        assertThat(message.status()).isEqualTo(InboxStatus.PROCESSED);
    }
}
