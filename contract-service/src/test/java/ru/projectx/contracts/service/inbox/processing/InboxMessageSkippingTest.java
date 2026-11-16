package ru.projectx.contracts.service.inbox.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.inbox.InboxMessage;
import ru.projectx.contracts.entity.inbox.InboxReceipt;
import ru.projectx.contracts.entity.inbox.InboxStatus;
import ru.projectx.contracts.repository.journal.InboxMessageRepository;
import ru.projectx.contracts.service.inbox.DecidingInboxWork;
import ru.projectx.contracts.service.inbox.InboxEventHandler;
import ru.projectx.contracts.service.inbox.InboxOrdering;
import ru.projectx.contracts.service.inbox.InboxWork;
import ru.projectx.contracts.service.leadership.LeadershipFence;
import ru.projectx.contracts.service.leadership.LeadershipLostException;

class InboxMessageSkippingTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final String PAYLOAD = "{\"eventId\":\"d1a0c001-5e2f-4a3b-8c4d-000000000001\"}";
    private static final InboxReceipt RECEIPT = new InboxReceipt(
            UUID.fromString("d1a0c001-5e2f-4a3b-8c4d-000000000001"),
            UUID.fromString("0196f0a2-0000-7000-8000-000000000011"), "ordering.order.events", "PaymentOrderEvent", 0);

    private final InboxMessageRepository messages = mock();
    private final InboxEventHandlers handlers = mock();
    private final InboxEventHandler handler = mock();
    private final PlatformTransactionManager transactions = mock();
    private final LeadershipFence fence = mock();
    private final InboxMessageHandling handling = new InboxMessageHandling(messages, handlers,
            new TransactionTemplate(transactions), fence, Clock.fixed(NOW, ZoneOffset.UTC));
    private final InboxMessage message = InboxMessage.receive(RECEIPT, PAYLOAD, NOW.minusSeconds(60));

    @BeforeEach
    void messageIsStoredAndItsHandlerKeepsNoOrder() {
        given(messages.findById(message.id())).willReturn(Optional.of(message));
        given(handlers.forEventType("PaymentOrderEvent")).willReturn(handler);
        given(handler.ordering()).willReturn(InboxOrdering.NONE);
    }

    @Test
    void workThatDecidesToSkip_marksTheMessageSkippedWithItsReasonAtTheMomentOfTheDecision() {
        prepared((DecidingInboxWork) now -> Optional.of("событие по чужому заказу"));

        handling.process(message.id());

        assertThat(message.status()).isEqualTo(InboxStatus.SKIPPED);
        assertThat(message.errorMessage()).contains("событие по чужому заказу");
        assertThat(message).hasFieldOrPropertyWithValue("processedAt", NOW);
        verify(transactions, org.mockito.Mockito.times(2)).commit(any());
    }

    @Test
    void workThatDecidesToApply_marksTheMessageProcessedWithoutAReason() {
        List<Instant> applied = new ArrayList<>();
        prepared((DecidingInboxWork) now -> {
            applied.add(now);
            return Optional.empty();
        });

        handling.process(message.id());

        assertThat(applied).containsExactly(NOW);
        assertThat(message.status()).isEqualTo(InboxStatus.PROCESSED);
        assertThat(message.errorMessage()).isEmpty();
        assertThat(message).hasFieldOrPropertyWithValue("processedAt", NOW);
    }

    @Test
    void plainWork_isAppliedAndTheMessageIsProcessed() {
        List<Instant> applied = new ArrayList<>();
        prepared(applied::add);

        handling.process(message.id());

        assertThat(applied).containsExactly(NOW);
        assertThat(message.status()).isEqualTo(InboxStatus.PROCESSED);
    }

    @Test
    void leaderLockRow_isHeldAfterTheDecisionAndBeforeTheSkipIsCommitted() {
        InboxWork work = mock();
        given(work.applyOrSkip(NOW)).willReturn(Optional.of("повтор события"));
        prepared(work);

        handling.process(message.id());

        InOrder order = inOrder(work, fence, transactions);
        order.verify(fence).holdUntilCommit();
        order.verify(work).applyOrSkip(NOW);
        order.verify(fence).holdUntilCommit();
        order.verify(transactions).commit(any());
        assertThat(message.status()).isEqualTo(InboxStatus.SKIPPED);
    }

    @Test
    void leaderLockLostAfterTheDecisionToSkip_leavesTheMessageNew() {
        prepared((DecidingInboxWork) now -> Optional.of("повтор события"));
        willDoNothing().willThrow(new LeadershipLostException("0b5f3c1e")).given(fence).holdUntilCommit();

        assertThatThrownBy(() -> handling.process(message.id())).isInstanceOf(LeadershipLostException.class);

        assertThat(message.status()).isEqualTo(InboxStatus.NEW);
        assertThat(message.errorMessage()).isEmpty();
        verify(transactions).rollback(any());
    }

    private void prepared(InboxWork work) {
        given(handler.prepare(PAYLOAD)).willReturn(work);
    }
}
