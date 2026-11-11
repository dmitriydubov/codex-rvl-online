package ru.projectx.contracts.entity.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PaymentOrderPayerTest {

    private static final UUID CONTRACT = UUID.fromString("0196f0a2-0000-7000-8000-000000000001");
    private static final UUID CLIENT = UUID.fromString("ae8312c3-350b-4d0b-806c-5e2460e74dbc");
    private static final Instant OPENED = Instant.parse("2026-02-10T07:05:00Z");
    private static final Instant LATER = Instant.parse("2026-02-10T07:06:00Z");
    private static final String ORDER = "0196f0a2-5b1c-7d3e-9f40-1a2b3c4d5e01";
    private static final PayerEmail PAYER = new PayerEmail("insured@example.test");
    private static final OrderDocument DOCUMENT = new OrderDocument("DKM-2026-000001", new BigDecimal("34000.00"),
            Instant.parse("2026-02-10T07:04:58Z"), Instant.parse("2026-04-09T20:59:59Z"));

    private static PaymentOrder opened() {
        return PaymentOrder.open(new OrderDraft(CONTRACT, CLIENT, DOCUMENT, PAYER), OPENED);
    }

    private static Object updatedAt(PaymentOrder order) {
        return ReflectionTestUtils.getField(order, "updatedAt");
    }

    @Test
    void openedOrder_keepsThePayerAddressOfTheAcceptance() {
        PaymentOrder order = opened();

        assertThat(order.view().payer()).contains(PAYER);
        assertThat(order.view().payer().orElseThrow().address()).isEqualTo("insured@example.test");
        assertThat(updatedAt(order)).isEqualTo(OPENED);
        assertThat(ReflectionTestUtils.getField(order, "createdAt")).isEqualTo(OPENED);
    }

    @Test
    void expire_ofAwaitingOrderNeverSentToOrdering_closesItByTermAndErasesTheAddress() {
        PaymentOrder order = opened();

        assertThat(order.expire(LATER)).isTrue();

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.EXPIRED);
        assertThat(order.view().payer()).isEmpty();
        assertThat(updatedAt(order)).isEqualTo(LATER);
    }

    @Test
    void expire_ofOrderWhoseCreationWasSent_refusesBecauseTheOrderMayExistInOrdering() {
        PaymentOrder order = opened();
        order.markRequested(OrderRequest.CREATION, OPENED);

        assertThat(order.expire(LATER)).isFalse();

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.AWAITING_CREATION);
        assertThat(order.view().payer()).contains(PAYER);
        assertThat(updatedAt(order)).isEqualTo(OPENED);
    }

    @Test
    void expire_ofCreatedOrder_refusesAndKeepsTheAddress() {
        PaymentOrder order = opened();
        order.recordOrder(ORDER, OPENED);

        assertThat(order.expire(LATER)).isFalse();

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CREATED);
        assertThat(order.view().payer()).contains(PAYER);
    }

    @Test
    void forgetPayer_erasesTheAddressOnce() {
        PaymentOrder order = opened();

        order.forgetPayer(LATER);

        assertThat(order.view().payer()).isEmpty();
        assertThat(order.view().state()).isEqualTo(PaymentOrderState.AWAITING_CREATION);
        assertThat(updatedAt(order)).isEqualTo(LATER);
    }

    @Test
    void forgetPayer_withoutAddress_changesNothing() {
        PaymentOrder order = opened();
        order.forgetPayer(OPENED);

        order.forgetPayer(LATER);

        assertThat(order.view().payer()).isEmpty();
        assertThat(updatedAt(order)).isEqualTo(OPENED);
    }

    @Test
    void withdraw_ofOrderNeverSentToOrdering_erasesTheAddress() {
        PaymentOrder order = opened();

        order.withdraw(LATER);

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CANCELLED);
        assertThat(order.view().payer()).isEmpty();
        assertThat(updatedAt(order)).isEqualTo(LATER);
    }

    @Test
    void withdraw_ofCreatedOrder_erasesTheAddress() {
        PaymentOrder order = opened();
        order.recordOrder(ORDER, OPENED);

        order.withdraw(LATER);

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
        assertThat(order.view().payer()).isEmpty();
    }

    @Test
    void withdraw_ofExpiredOrder_keepsItExpired() {
        PaymentOrder order = opened();
        order.expire(OPENED);

        order.withdraw(LATER);

        assertThat(order.view().state()).isEqualTo(PaymentOrderState.EXPIRED);
        assertThat(updatedAt(order)).isEqualTo(OPENED);
    }

    @Test
    void recordOrder_keepsTheAddressUntilTheInvoice() {
        PaymentOrder order = opened();

        order.recordOrder(ORDER, LATER);

        assertThat(order.view().payer()).contains(PAYER);
    }
}
