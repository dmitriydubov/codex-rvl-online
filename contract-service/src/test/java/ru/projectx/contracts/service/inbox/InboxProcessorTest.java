package ru.projectx.contracts.service.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.config.journal.InboxProperties;
import ru.projectx.contracts.entity.inbox.InboxMessage;
import ru.projectx.contracts.entity.inbox.InboxReceipt;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.repository.InboxMessageRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;
import ru.projectx.contracts.service.leadership.LeadershipLostException;
import ru.projectx.contracts.support.TestLeadership;

class InboxProcessorTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");

    private final InboxMessageRepository messages = mock();
    private final InboxMessageHandling handling = mock();
    private final InboxOutcomes outcomes = mock();
    private final BackgroundLeadership leadership = TestLeadership.leader();
    private final InboxProcessor processor = processor(leadership);
    private final InboxMessage first = message("b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11");
    private final InboxMessage second = message("0c7d2e4f-6a8b-4c0d-9e1f-2a3b4c5d6e7f");

    @Test
    void dueMessages_areHandledInOrderWithinTheBatchLimit() {
        given(messages.findDue(NOW, Limit.of(7))).willReturn(List.of(first, second));

        assertThat(processor.processDue()).isEqualTo(2);

        verify(handling).process(first.id());
        verify(handling).process(second.id());
        verifyNoInteractions(outcomes);
    }

    @Test
    void failedMessage_getsItsOutcomeRecordedAndTheOthersAreStillHandled() {
        given(messages.findDue(NOW, Limit.of(7))).willReturn(List.of(first, second));
        willThrow(new ContractViolationException("нет обязательного поля data.date_from")).given(handling).process(first.id());

        processor.processDue();

        verify(outcomes).recordFailure(first.id(),
                new InboxFailure.Permanent("нет обязательного поля data.date_from", "нет"));
        verify(handling).process(second.id());
    }

    @Test
    void standby_processesNothingAndDoesNotReadTheJournal() {
        assertThat(processor(TestLeadership.standby()).processDue()).isZero();

        verifyNoInteractions(messages, handling, outcomes);
    }

    @Test
    void lostLeaderLockInTheMiddleOfThePass_endsItWithoutRecordingAFailure() {
        given(messages.findDue(NOW, Limit.of(7))).willReturn(List.of(first, second));
        willThrow(new LeadershipLostException("0b5f3c1e")).given(handling).process(first.id());

        assertThat(processor.processDue()).isZero();

        verify(handling, never()).process(second.id());
        verifyNoInteractions(outcomes);
    }

    @Test
    void nothingDue_handlesNothing() {
        given(messages.findDue(NOW, Limit.of(7))).willReturn(List.of());

        assertThat(processor.processDue()).isZero();
        verifyNoInteractions(handling, outcomes);
    }

    private InboxProcessor processor(BackgroundLeadership role) {
        return new InboxProcessor(messages, handling, outcomes,
                new InboxProperties(new InboxProperties.Processing(Duration.ofSeconds(10), 7),
                        new InboxProperties.Retry(List.of(Duration.ofMinutes(1))),
                        new InboxProperties.Retention(Duration.ofDays(30), Duration.ofHours(1), 500),
                        new InboxProperties.Pause(Duration.ofSeconds(10), Duration.ofSeconds(30)), Duration.ofSeconds(5)),
                role, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static InboxMessage message(String messageId) {
        InboxMessage message = InboxMessage.receive(new InboxReceipt(UUID.fromString(messageId),
                UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803"), "topic", "ContractTaskUpserted", 1),
                "{}", NOW);
        ReflectionTestUtils.setField(message, "id", UUID.fromString(messageId));
        return message;
    }
}
