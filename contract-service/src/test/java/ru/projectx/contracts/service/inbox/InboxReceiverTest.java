package ru.projectx.contracts.service.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import ru.projectx.contracts.entity.inbox.InboxMessage;
import ru.projectx.contracts.entity.inbox.InboxReceipt;
import ru.projectx.contracts.entity.inbox.InboxStatus;
import ru.projectx.contracts.repository.InboxMessageRepository;
import ru.projectx.contracts.service.leadership.LeadershipFence;
import ru.projectx.contracts.service.leadership.LeadershipLostException;

class InboxReceiverTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final InboxReceipt RECEIPT = new InboxReceipt(
            UUID.fromString("b81f0f3a-1c44-4f2a-9a1e-5a0e2f7c9d11"),
            UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803"),
            "rvl.contract-task.upserted", "ContractTaskUpserted", 1);

    private final InboxMessageRepository messages = mock();
    private final LeadershipFence fence = mock();
    private final InboxReceiver receiver = new InboxReceiver(messages, fence, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void newMessage_isStoredWithReceiptBodyAndReceiptTime() {
        given(messages.existsByMessageId(RECEIPT.messageId())).willReturn(false);

        assertThat(receiver.receive(RECEIPT, "{\"meta\":{}}")).isEqualTo(ReceiptOutcome.STORED);

        ArgumentCaptor<InboxMessage> saved = ArgumentCaptor.captor();
        verify(messages).save(saved.capture());
        assertThat(saved.getValue().receipt()).isEqualTo(RECEIPT);
        assertThat(saved.getValue().payload()).contains("{\"meta\":{}}");
        assertThat(saved.getValue().status()).isEqualTo(InboxStatus.NEW);
        assertThat(saved.getValue()).hasFieldOrPropertyWithValue("receivedAt", NOW);
    }

    @Test
    void leaderLockRow_isHeldAfterTheDuplicateCheckAndBeforeTheMessageIsStored() {
        given(messages.existsByMessageId(RECEIPT.messageId())).willReturn(false);

        receiver.receive(RECEIPT, "{}");

        InOrder order = inOrder(fence, messages);
        order.verify(messages).existsByMessageId(RECEIPT.messageId());
        order.verify(fence).holdUntilCommit();
        order.verify(messages).save(any());
    }

    @Test
    void lostLeaderLock_failsTheReceiptBeforeAnythingIsStored() {
        willThrow(new LeadershipLostException("0b5f3c1e")).given(fence).holdUntilCommit();

        assertThatThrownBy(() -> receiver.receive(RECEIPT, "{}")).isInstanceOf(LeadershipLostException.class);
        verify(messages, never()).save(any());
    }

    @Test
    void lostLeaderLock_failsEvenTheReceiptOfADuplicate() {
        given(messages.existsByMessageId(RECEIPT.messageId())).willReturn(true);
        willThrow(new LeadershipLostException("0b5f3c1e")).given(fence).holdUntilCommit();

        assertThatThrownBy(() -> receiver.receive(RECEIPT, "{}")).isInstanceOf(LeadershipLostException.class);
    }

    @Test
    void knownMessageId_isDuplicateAndNotStoredAgain() {
        given(messages.existsByMessageId(RECEIPT.messageId())).willReturn(true);

        assertThat(receiver.receive(RECEIPT, "{}")).isEqualTo(ReceiptOutcome.DUPLICATE);
        verify(messages, never()).save(any());
    }
}
