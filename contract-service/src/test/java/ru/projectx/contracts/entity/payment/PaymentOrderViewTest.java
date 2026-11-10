package ru.projectx.contracts.entity.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentOrderViewTest {

    private static final UUID ID = UUID.fromString("0196f0a2-0000-7000-8000-000000000011");
    private static final OrderDocument DOCUMENT = new OrderDocument("DKM-2026-000001", new BigDecimal("34000.00"),
            Instant.parse("2026-02-10T07:04:58Z"), Instant.parse("2026-04-09T20:59:59Z"));

    private static PaymentOrderView view(Optional<String> orderId, OrderRequests requests) {
        return new PaymentOrderView(ID, ID, ID, ID, DOCUMENT, PaymentOrderState.AWAITING_CREATION, orderId,
                Optional.empty(), requests, Optional.empty());
    }

    @Test
    void creationRequest_isReadFromTheRequests() {
        PaymentOrderView view = view(Optional.empty(), new OrderRequests(true, false));

        assertThat(view.creationRequested()).isTrue();
        assertThat(view.cancelRequested()).isFalse();
    }

    @Test
    void cancellationRequest_isReadFromTheRequests() {
        PaymentOrderView view = view(Optional.empty(), new OrderRequests(false, true));

        assertThat(view.creationRequested()).isFalse();
        assertThat(view.cancelRequested()).isTrue();
    }

    @Test
    void orderWithoutNumberAndWithoutCreationRequest_cannotExistInOrdering() {
        assertThat(view(Optional.empty(), new OrderRequests(false, true)).mayExistInOrdering()).isFalse();
    }

    @Test
    void orderWithNumber_mayExistInOrdering() {
        assertThat(view(Optional.of("order-1"), new OrderRequests(false, false)).mayExistInOrdering()).isTrue();
    }

    @Test
    void orderWhoseCreationWasSent_mayExistInOrdering() {
        assertThat(view(Optional.empty(), new OrderRequests(true, false)).mayExistInOrdering()).isTrue();
    }
}
