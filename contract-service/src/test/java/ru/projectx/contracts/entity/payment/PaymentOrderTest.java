package ru.projectx.contracts.entity.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentOrderTest {

    private static final UUID CONTRACT = UUID.fromString("0196f0a2-0000-7000-8000-000000000001");
    private static final UUID CLIENT = UUID.fromString("ae8312c3-350b-4d0b-806c-5e2460e74dbc");
    private static final Instant OPENED = Instant.parse("2026-02-10T07:05:00Z");
    private static final Instant LATER = Instant.parse("2026-02-10T07:06:00Z");
    private static final String ORDER = "0196f0a2-5b1c-7d3e-9f40-1a2b3c4d5e01";
    private static final OrderDocument DOCUMENT = new OrderDocument("DKM-2026-000001", new BigDecimal("34000.00"),
            Instant.parse("2026-02-10T07:04:58Z"), Instant.parse("2026-04-09T20:59:59Z"));

    private static PaymentOrder opened() {
        return PaymentOrder.open(CONTRACT, CLIENT, DOCUMENT, OPENED);
    }

    @Test
    void openedOrder_awaitsCreationWithTheSnapshotAndNoRequestsYet() {
        PaymentOrderView view = opened().view();

        assertThat(view.state()).isEqualTo(PaymentOrderState.AWAITING_CREATION);
        assertThat(view.contractId()).isEqualTo(CONTRACT);
        assertThat(view.clientId()).isEqualTo(CLIENT);
        assertThat(view.correlationId()).isNotNull().isNotEqualTo(opened().view().correlationId());
        assertThat(view.document()).isEqualTo(DOCUMENT);
        assertThat(view.orderId()).isEmpty();
        assertThat(view.rejectionCode()).isEmpty();
        assertThat(view.creationRequested()).isFalse();
        assertThat(view.cancelRequested()).isFalse();
    }

    @Test
    void requestCreation_marksThatCreationWasSent() {
        PaymentOrder order = opened();

        order.requestCreation(LATER);

        assertThat(order.view().creationRequested()).isTrue();
    }

    @Test
    void recordOrder_onAwaitingOrder_createsItAndAsksToConfirmTheDocument() {
        PaymentOrder order = opened();

        assertThat(order.recordOrder(ORDER, LATER)).isTrue();

        assertThat(order.state()).isEqualTo(PaymentOrderState.CREATED);
        assertThat(order.view().orderId()).contains(ORDER);
    }

    @Test
    void recordOrder_secondTime_keepsTheFirstOrder() {
        PaymentOrder order = opened();
        order.recordOrder(ORDER, LATER);

        assertThat(order.recordOrder("another", LATER)).isFalse();

        assertThat(order.view().orderId()).contains(ORDER);
        assertThat(order.state()).isEqualTo(PaymentOrderState.CREATED);
    }

    @Test
    void recordOrder_whileCancelPending_keepsItPendingWithoutConfirmation() {
        PaymentOrder order = opened();
        order.requestCreation(LATER);
        order.withdraw(LATER);

        assertThat(order.recordOrder(ORDER, LATER)).isFalse();

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
        assertThat(order.view().orderId()).contains(ORDER);
    }

    @Test
    void recordOrder_afterTheRecordWasClosed_returnsItToCancelPending() {
        PaymentOrder order = opened();
        order.withdraw(LATER);

        assertThat(order.recordOrder(ORDER, LATER)).isFalse();

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
    }

    @Test
    void reject_awaitingOrder_keepsTheCode() {
        PaymentOrder order = opened();

        assertThat(order.reject("CreateOrderValidatorService.valid", LATER)).isTrue();

        assertThat(order.state()).isEqualTo(PaymentOrderState.REJECTED);
        assertThat(order.view().rejectionCode()).contains("CreateOrderValidatorService.valid");
    }

    @Test
    void reject_createdOrder_keepsTheOrderId() {
        PaymentOrder order = opened();
        order.recordOrder(ORDER, LATER);

        assertThat(order.reject("FIXED_UNDELIVERED", LATER)).isTrue();

        assertThat(order.state()).isEqualTo(PaymentOrderState.REJECTED);
        assertThat(order.view().orderId()).contains(ORDER);
    }

    @Test
    void reject_withdrawnOrder_changesNothing() {
        PaymentOrder order = opened();
        order.withdraw(LATER);

        assertThat(order.reject("code", LATER)).isFalse();

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCELLED);
        assertThat(order.view().rejectionCode()).isEmpty();
    }

    @Test
    void withdraw_beforeAnyRequest_closesTheOrder() {
        PaymentOrder order = opened();

        order.withdraw(LATER);

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCELLED);
    }

    @Test
    void withdraw_afterCreationWasSent_waitsForCancellation() {
        PaymentOrder order = opened();
        order.requestCreation(LATER);

        order.withdraw(LATER);

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
    }

    @Test
    void withdraw_createdOrder_waitsForCancellation() {
        PaymentOrder order = opened();
        order.recordOrder(ORDER, LATER);

        order.withdraw(LATER);

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
    }

    @Test
    void withdraw_rejectedAfterCreationWasSent_waitsForCancellationBecauseTheOrderMayExist() {
        PaymentOrder order = opened();
        order.requestCreation(LATER);
        order.reject("code", LATER);

        order.withdraw(LATER);

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
    }

    @Test
    void withdraw_rejectedWithoutCreationRequest_closesIt() {
        PaymentOrder order = opened();
        order.reject("code", LATER);

        order.withdraw(LATER);

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCELLED);
    }

    @Test
    void withdraw_rejectedWithOrder_waitsForCancellation() {
        PaymentOrder order = opened();
        order.recordOrder(ORDER, LATER);
        order.reject("FIXED_UNDELIVERED", LATER);

        order.withdraw(LATER);

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
    }

    @Test
    void withdraw_twice_keepsTheFirstOutcome() {
        PaymentOrder order = opened();
        order.recordOrder(ORDER, LATER);
        order.withdraw(LATER);
        order.recordCancelled(LATER);

        order.withdraw(LATER);

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCELLED);
    }

    @Test
    void cancellationFlow_marksRequestAndCloses() {
        PaymentOrder order = opened();
        order.recordOrder(ORDER, LATER);
        order.withdraw(LATER);

        order.requestCancellation(LATER);

        assertThat(order.view().cancelRequested()).isTrue();
        assertThat(order.recordCancelled(LATER)).isTrue();
        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCELLED);
    }

    @Test
    void recordCancelled_notPending_changesNothing() {
        PaymentOrder order = opened();

        assertThat(order.recordCancelled(LATER)).isFalse();

        assertThat(order.state()).isEqualTo(PaymentOrderState.AWAITING_CREATION);
    }

    @Test
    void rejectCancellation_pending_keepsTheCode() {
        PaymentOrder order = opened();
        order.recordOrder(ORDER, LATER);
        order.withdraw(LATER);

        assertThat(order.rejectCancellation("CancelOrderValidatorService.valid", LATER)).isTrue();

        assertThat(order.state()).isEqualTo(PaymentOrderState.CANCEL_REJECTED);
        assertThat(order.view().rejectionCode()).isEqualTo(Optional.of("CancelOrderValidatorService.valid"));
    }

    @Test
    void rejectCancellation_notPending_changesNothing() {
        PaymentOrder order = opened();

        assertThat(order.rejectCancellation("code", LATER)).isFalse();

        assertThat(order.state()).isEqualTo(PaymentOrderState.AWAITING_CREATION);
        assertThat(order.view().rejectionCode()).isEmpty();
    }

    @Test
    void withdrawnStates_areTheCancellationOnes() {
        assertThat(PaymentOrderState.CANCEL_PENDING.withdrawn()).isTrue();
        assertThat(PaymentOrderState.CANCELLED.withdrawn()).isTrue();
        assertThat(PaymentOrderState.CANCEL_REJECTED.withdrawn()).isTrue();
        assertThat(PaymentOrderState.AWAITING_CREATION.withdrawn()).isFalse();
        assertThat(PaymentOrderState.CREATED.withdrawn()).isFalse();
        assertThat(PaymentOrderState.REJECTED.withdrawn()).isFalse();
    }
}
