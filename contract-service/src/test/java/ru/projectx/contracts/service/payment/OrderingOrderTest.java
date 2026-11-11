package ru.projectx.contracts.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderingOrderTest {

    private static final Instant EARLIER = Instant.parse("2026-02-10T07:05:03.250Z");
    private static final Instant LATER = Instant.parse("2026-02-10T07:06:00Z");

    @Test
    void order_isInItsOwnStatusOnly() {
        OrderingOrder order = new OrderingOrder("PAID", List.of());

        assertThat(order.in("PAID")).isTrue();
        assertThat(order.in("PENDING_PAYMENT")).isFalse();
    }

    @Test
    void lastMoment_isTheLatestEventOfTheAskedType() {
        OrderingOrder order = new OrderingOrder("PAID", List.of(new OrderingEvent("paid", EARLIER),
                new OrderingEvent("paid", LATER), new OrderingEvent("paymentError", LATER.plusSeconds(60))));

        assertThat(order.lastMoment("paid")).contains(LATER);
    }

    @Test
    void lastMoment_doesNotDependOnTheOrderOfTheHistory() {
        OrderingOrder order = new OrderingOrder("PAID",
                List.of(new OrderingEvent("paid", LATER), new OrderingEvent("paid", EARLIER)));

        assertThat(order.lastMoment("paid")).contains(LATER);
    }

    @Test
    void lastMoment_ofTypeMissingInTheHistory_isUnknown() {
        OrderingOrder order = new OrderingOrder("PAID", List.of(new OrderingEvent("fixed", EARLIER)));

        assertThat(order.lastMoment("paid")).isEmpty();
    }

    @Test
    void history_isCopiedAndCannotBeChangedAfterwards() {
        List<OrderingEvent> source = new ArrayList<>(List.of(new OrderingEvent("paid", EARLIER)));
        OrderingOrder order = new OrderingOrder("PAID", source);

        source.add(new OrderingEvent("paid", LATER));

        assertThat(order.events()).containsExactly(new OrderingEvent("paid", EARLIER));
        assertThatThrownBy(() -> order.events().add(new OrderingEvent("paid", LATER)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
