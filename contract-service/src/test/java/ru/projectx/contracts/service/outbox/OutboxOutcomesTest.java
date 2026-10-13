package ru.projectx.contracts.service.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.config.OutboxProperties;
import ru.projectx.contracts.entity.OutboxDraft;
import ru.projectx.contracts.entity.MessageEnvelope;
import ru.projectx.contracts.entity.OutboxMessage;
import ru.projectx.contracts.entity.OutboxStatus;
import ru.projectx.contracts.repository.OutboxMessageRepository;

class OutboxOutcomesTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final UUID ID = UUID.fromString("01960000-0000-7000-8000-000000000001");
    private static final UUID MESSAGE_ID = UUID.fromString("7d2a41c9-9f01-4f77-8a1c-2b9cf0e5a442");
    private static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    private static final MessageEnvelope ENVELOPE = new MessageEnvelope(MESSAGE_ID, REQUEST,
            "rvl.contract-status.changed", "ContractStatusChanged", 1);

    private final OutboxMessageRepository messages = mock();
    private final OutboxMetrics metrics = mock();
    private final OutboxOutcomes outcomes = new OutboxOutcomes(messages, properties(), metrics,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final OutboxMessage message = OutboxMessage.compose(
            new OutboxDraft("INDIVIDUAL_REQUEST", REQUEST, ENVELOPE, "{}"), NOW.minusSeconds(5));
    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();
    private final Logger log = (Logger) LoggerFactory.getLogger(OutboxOutcomes.class);

    @BeforeEach
    void messageIsStored() {
        ReflectionTestUtils.setField(message, "id", ID);
        given(messages.findById(ID)).willReturn(Optional.of(message));
        journal.start();
        log.addAppender(journal);
    }

    @AfterEach
    void stopJournal() {
        log.detachAppender(journal);
    }

    @Test
    void delivered_marksTheMessagePublishedNow() {
        outcomes.record(ID, new PublishOutcome.Delivered());

        assertThat(message.status()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(message.attempts()).isEqualTo(1);
        assertThat(message).hasFieldOrPropertyWithValue("publishedAt", NOW);
        verify(metrics, never()).countFailed(anyString());
        assertThat(journal.list).isEmpty();
    }

    @Test
    void refused_rejectsAtTheFirstAttemptWithErrorLogAndCounter() {
        outcomes.record(ID, new PublishOutcome.Refused("брокер вернул сообщение: NO_ROUTE (312)"));

        assertThat(message.status()).isEqualTo(OutboxStatus.FAILED);
        assertThat(message.attempts()).isEqualTo(1);
        assertThat(message.errorMessage()).contains("брокер вернул сообщение: NO_ROUTE (312)");
        verify(metrics).countFailed("ContractStatusChanged");
        assertThat(journal.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).isEqualTo("исходящее сообщение " + MESSAGE_ID
                    + " отклонено окончательно: тип события ContractStatusChanged, попытка 1, причина: "
                    + "брокер вернул сообщение: NO_ROUTE (312)");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void firstRejection_schedulesRetryAfterFirstPause() {
        outcomes.record(ID, new PublishOutcome.Rejected("брокер отклонил сообщение (nack)"));

        assertThat(message.status()).isEqualTo(OutboxStatus.NEW);
        assertThat(message.attempts()).isEqualTo(1);
        assertThat(message.errorMessage()).contains("брокер отклонил сообщение (nack)");
        assertThat(message).hasFieldOrPropertyWithValue("nextAttemptAt", NOW.plus(Duration.ofMinutes(1)));
        verify(metrics, never()).countFailed(anyString());
        assertThat(journal.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains(MESSAGE_ID.toString()).contains("после попытки 1");
        });
    }

    @Test
    void thirdUnconfirmedAttempt_schedulesRetryAfterLastPause() {
        message.markRetry("отказ", NOW);
        message.markRetry("отказ", NOW);

        outcomes.record(ID, new PublishOutcome.Unconfirmed(Duration.ofSeconds(10)));

        assertThat(message.attempts()).isEqualTo(3);
        assertThat(message.errorMessage()).contains("подтверждение брокера не получено за PT10S");
        assertThat(message).hasFieldOrPropertyWithValue("nextAttemptAt", NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void fourthTransientFailure_rejectsTheMessageAndCountsIt() {
        message.markRetry("отказ", NOW);
        message.markRetry("отказ", NOW);
        message.markRetry("отказ", NOW);

        outcomes.record(ID, new PublishOutcome.Rejected("брокер отклонил сообщение (nack)"));

        assertThat(message.status()).isEqualTo(OutboxStatus.FAILED);
        assertThat(message.attempts()).isEqualTo(4);
        assertThat(message.errorMessage())
                .contains("повторы исчерпаны на попытке 4: брокер отклонил сообщение (nack)");
        verify(metrics).countFailed("ContractStatusChanged");
        assertThat(journal.list).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage()).contains("попытка 4, причина: повторы исчерпаны на попытке 4"));
    }

    @Test
    void outcomeRecordedAfterAnotherPassFinishedTheMessage_changesNothing() {
        message.markPublished(NOW);

        outcomes.record(ID, new PublishOutcome.Refused("отказ"));

        assertThat(message.status()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(message.attempts()).isEqualTo(1);
        verify(metrics, never()).countFailed(anyString());
        assertThat(journal.list).singleElement().satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    }

    @Test
    void unknownId_isIllegalState() {
        UUID unknown = UUID.fromString("00000000-0000-4000-8000-000000000001");
        given(messages.findById(unknown)).willReturn(Optional.empty());

        assertThatThrownBy(() -> outcomes.record(unknown, new PublishOutcome.Delivered()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static OutboxProperties properties() {
        return new OutboxProperties(new OutboxProperties.Publishing(Duration.ofSeconds(5), 100),
                Duration.ofSeconds(10),
                new OutboxProperties.Retry(List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15))),
                new OutboxProperties.Retention(Duration.ofDays(30), Duration.ofHours(1), 500));
    }
}
