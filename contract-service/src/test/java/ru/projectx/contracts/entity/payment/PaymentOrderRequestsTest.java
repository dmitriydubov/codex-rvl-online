package ru.projectx.contracts.entity.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PaymentOrderRequestsTest {

    private static final UUID CONTRACT = UUID.fromString("0196f0a2-0000-7000-8000-000000000001");
    private static final UUID CLIENT = UUID.fromString("ae8312c3-350b-4d0b-806c-5e2460e74dbc");
    private static final Instant OPENED = Instant.parse("2026-02-10T07:05:00Z");
    private static final Instant LATER = Instant.parse("2026-02-10T07:06:00Z");
    private static final String ORDER = "0196f0a2-5b1c-7d3e-9f40-1a2b3c4d5e01";
    private static final OrderDocument DOCUMENT = new OrderDocument("DKM-2026-000001", new BigDecimal("34000.00"),
            Instant.parse("2026-02-10T07:04:58Z"), Instant.parse("2026-04-09T20:59:59Z"));

    private static PaymentOrder awaiting() {
        return PaymentOrder.open(new OrderDraft(CONTRACT, CLIENT, DOCUMENT, new PayerEmail("insured@example.test")),
                OPENED);
    }

    private static PaymentOrder cancelPending() {
        PaymentOrder order = awaiting();
        order.recordOrder(ORDER, OPENED);
        order.withdraw(OPENED);
        return order;
    }

    @Test
    void creationRequest_marksOnlyThatCreationWasSent() {
        PaymentOrder order = awaiting();

        order.markRequested(OrderRequest.CREATION, LATER);

        assertThat(order.view().requests()).isEqualTo(new OrderRequests(true, false));
        assertThat(ReflectionTestUtils.getField(order, "updatedAt")).isEqualTo(LATER);
    }

    @Test
    void cancellationRequest_marksOnlyThatCancellationWasSent() {
        PaymentOrder order = cancelPending();

        order.markRequested(OrderRequest.CANCELLATION, LATER);

        assertThat(order.view().requests()).isEqualTo(new OrderRequests(false, true));
        assertThat(ReflectionTestUtils.getField(order, "updatedAt")).isEqualTo(LATER);
    }

    @Test
    void creationRequest_ofOrderThatMeanwhileWaitsForCancellation_stillMarksTheCreation() {
        PaymentOrder order = cancelPending();

        order.markRequested(OrderRequest.CREATION, LATER);

        assertThat(order.view().requests()).isEqualTo(new OrderRequests(true, false));
        assertThat(order.view().state()).isEqualTo(PaymentOrderState.CANCEL_PENDING);
    }

    @Test
    void cancellationRequest_doesNotDependOnTheStateOfTheOrder() {
        PaymentOrder order = awaiting();

        order.markRequested(OrderRequest.CANCELLATION, LATER);

        assertThat(order.view().requests()).isEqualTo(new OrderRequests(false, true));
        assertThat(order.view().state()).isEqualTo(PaymentOrderState.AWAITING_CREATION);
    }

    @Test
    void bothRequests_areKeptSideBySide() {
        PaymentOrder order = awaiting();

        order.markRequested(OrderRequest.CREATION, OPENED);
        order.markRequested(OrderRequest.CANCELLATION, LATER);

        assertThat(order.view().requests()).isEqualTo(new OrderRequests(true, true));
    }
}
