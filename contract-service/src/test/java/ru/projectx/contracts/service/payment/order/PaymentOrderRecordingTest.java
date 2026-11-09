package ru.projectx.contracts.service.payment.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.Assertions;
import org.mockito.InOrder;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.outbox.OutboxDraft;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.service.leadership.LeadershipFence;
import ru.projectx.contracts.service.leadership.LeadershipLostException;
import ru.projectx.contracts.service.outbox.OutboxJournal;
import ru.projectx.contracts.service.payment.LockedOrder;
import ru.projectx.contracts.service.payment.OrderingMetrics;
import ru.projectx.contracts.service.payment.PaymentOrderLocks;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.PaymentOrders;

class PaymentOrderRecordingTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:10:00Z");

    private final PaymentOrderLocks locks = mock();
    private final DocumentFixMessages messages = mock();
    private final OutboxJournal journal = mock();
    private final OrderingMetrics metrics = mock();
    private final LeadershipFence fence = mock();
    private final PaymentOrderRecording recording = new PaymentOrderRecording(locks, messages, journal, metrics, fence,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final Contract contract = PaymentOrders.acceptedContract();
    private final OutboxDraft draft = mock();
    private CapturedLog log;

    @BeforeEach
    void captureLog() {
        log = CapturedLog.of(PaymentOrderRecording.class);
    }

    @AfterEach
    void releaseLog() {
        log.close();
    }

    @Test
    void requestCreation_ofAwaitingOrder_marksItUnderTheFence() {
        PaymentOrder order = locked(PaymentOrders.awaiting());

        assertThat(recording.requestCreation(PaymentOrders.CONTRACT)).isEqualTo(CreationClaim.FIRST);

        assertThat(order.view().creationRequested()).isTrue();
        verify(fence).holdUntilCommit();
    }

    @Test
    void requestCreation_ofAlreadyRequestedOrder_saysItIsARepeat() {
        PaymentOrder order = PaymentOrders.awaiting();
        order.requestCreation(NOW);
        locked(order);

        assertThat(recording.requestCreation(PaymentOrders.CONTRACT)).isEqualTo(CreationClaim.REPEATED);

        assertThat(order.view().creationRequested()).isTrue();
    }

    @Test
    void requestCreation_ofWithdrawnOrder_refusesWithoutTouchingIt() {
        PaymentOrder order = PaymentOrders.awaiting();
        order.withdraw(NOW);
        locked(order);

        assertThat(recording.requestCreation(PaymentOrders.CONTRACT)).isEqualTo(CreationClaim.WITHDRAWN);

        assertThat(order.view().creationRequested()).isFalse();
        verifyNoInteractions(fence);
    }

    @Test
    void created_awaitingOrder_recordsTheOrderAndJournalsTheDocumentFixAfterTheFence() {
        PaymentOrder order = locked(PaymentOrders.awaiting());
        given(messages.draft(contract, order, NOW)).willReturn(draft);

        recording.created(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID);

        assertThat(order.state()).isEqualTo(PaymentOrderState.CREATED);
        InOrder sequence = inOrder(locks, fence, journal);
        sequence.verify(locks).lock(PaymentOrders.CONTRACT);
        sequence.verify(fence).holdUntilCommit();
        sequence.verify(journal).record(draft, NOW);
        assertThat(log.at(Level.INFO)).containsExactly("заказ на оплату договора КМ " + PaymentOrders.CONTRACT
                + " участника 9275471c-d2fe-484f-97a6-d6d7b1267b5e заявки 61a7982c-a0b4-4bbf-9527-60b40b752803: "
                + "заказ создан в Ordering, orderId " + PaymentOrders.ORDER_ID);
    }

    @Test
    void created_whileCancelPending_keepsTheOrderIdWithoutDocumentFix() {
        PaymentOrder order = PaymentOrders.awaiting();
        order.requestCreation(NOW);
        order.withdraw(NOW);
        locked(order);

        recording.created(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID);

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
        assertThat(order.view().orderId()).contains(PaymentOrders.ORDER_ID);
        verifyNoInteractions(journal, messages);
        assertThat(log.at(Level.INFO)).singleElement().asString().contains("CANCEL_PENDING");
    }

    @Test
    void lateAnswerForAClosedRecord_returnsItToCancellationWithoutDocumentFix() {
        PaymentOrder order = PaymentOrders.awaiting();
        order.withdraw(NOW);
        locked(order);

        recording.created(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID);

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
        assertThat(order.view().orderId()).contains(PaymentOrders.ORDER_ID);
        verifyNoInteractions(journal, messages);
    }

    @Test
    void created_whenLeadershipIsLost_journalsNothing() {
        PaymentOrder order = locked(PaymentOrders.awaiting());
        willThrow(new LeadershipLostException("stand")).given(fence).holdUntilCommit();

        Assertions.assertThatThrownBy(() -> recording.created(PaymentOrders.CONTRACT, PaymentOrders.ORDER_ID))
                .isInstanceOf(LeadershipLostException.class);

        assertThat(order.state()).isEqualTo(PaymentOrderState.AWAITING_CREATION);
        verifyNoInteractions(journal);
    }

    @Test
    void rejected_awaitingOrder_isRejectedCountedAndLoggedAsError() {
        PaymentOrder order = locked(PaymentOrders.awaiting());

        recording.rejected(PaymentOrders.CONTRACT, "CreateOrderValidatorService.valid");

        assertThat(order.state()).isEqualTo(PaymentOrderState.REJECTED);
        verify(fence).holdUntilCommit();
        verify(metrics).countOrderRejected();
        assertThat(log.at(Level.ERROR)).singleElement().asString()
                .endsWith(": Ordering отклонил создание заказа, код CreateOrderValidatorService.valid");
    }

    @Test
    void rejected_whileCancelPending_closesTheRecordWithoutAlert() {
        PaymentOrder order = PaymentOrders.awaiting();
        order.requestCreation(NOW);
        order.withdraw(NOW);
        locked(order);

        recording.rejected(PaymentOrders.CONTRACT, "C");

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCELLED);
        verify(metrics, never()).countOrderRejected();
        assertThat(log.at(Level.ERROR)).isEmpty();
        assertThat(log.at(Level.INFO)).hasSize(1);
    }

    @Test
    void rejected_closedOrder_changesNothing() {
        PaymentOrder order = PaymentOrders.awaiting();
        order.withdraw(NOW);
        locked(order);

        recording.rejected(PaymentOrders.CONTRACT, "C");

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCELLED);
        verify(metrics, never()).countOrderRejected();
        assertThat(log.events()).isEmpty();
        verify(journal, never()).record(any(), any());
    }

    private PaymentOrder locked(PaymentOrder order) {
        given(locks.lock(PaymentOrders.CONTRACT)).willReturn(new LockedOrder(contract, order));
        return order;
    }
}
