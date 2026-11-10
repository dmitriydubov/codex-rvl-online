package ru.projectx.contracts.service.integration.ordering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingOrderFailureException;
import ru.projectx.contracts.service.payment.OrderingEvent;
import ru.projectx.contracts.service.payment.OrderingOrder;
import tools.jackson.databind.json.JsonMapper;

class OrderingOrdersTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void order_carriesItsStatusAndEventsWithMomentsBroughtToUtcInTheOrderOfTheReply() {
        OrderingOrder order = read("""
                {"status": "PAYMENT_ERROR", "events": [
                 {"eventType": "paymentError", "created": "2026-02-10T10:20:15+03:00"},
                 {"eventType": "pendingPayment", "created": "2026-02-10T07:05:01Z"}]}""");

        assertThat(order).isEqualTo(new OrderingOrder("PAYMENT_ERROR", List.of(
                new OrderingEvent("paymentError", Instant.parse("2026-02-10T07:20:15Z")),
                new OrderingEvent("pendingPayment", Instant.parse("2026-02-10T07:05:01Z")))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"status\": \"NEW\"}", "{\"status\": \"NEW\", \"events\": []}",
        "{\"status\": \"NEW\", \"events\": null}", "{\"status\": \"NEW\", \"events\": \"нет\"}"})
    void orderWithoutEvents_hasAnEmptyHistory(String data) {
        assertThat(read(data)).isEqualTo(new OrderingOrder("NEW", List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"created\": \"2026-02-10T10:20:15+03:00\"}",
        "{\"eventType\": \"\", \"created\": \"2026-02-10T10:20:15+03:00\"}",
        "{\"eventType\": \"paid\"}",
        "{\"eventType\": \"paid\", \"created\": \"\"}",
        "{\"eventType\": \"paid\", \"created\": \"вчера\"}",
        "{\"eventType\": \"paid\", \"created\": \"2026-02-10T10:20:15\"}",
        "{\"eventType\": \"paid\", \"created\": \"2026-02-30T10:20:15+03:00\"}"})
    void eventWithoutTypeOrWithoutAMomentWithOffset_isLeftOutAndTheOthersStay(String broken) {
        OrderingOrder order = read("{\"status\": \"PAID\", \"events\": [" + broken
                + ", {\"eventType\": \"paid\", \"created\": \"2026-02-10T10:20:15+03:00\"}]}");

        assertThat(order.events()).containsExactly(new OrderingEvent("paid", Instant.parse("2026-02-10T07:20:15Z")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\": 7}", "{\"status\": \"\"}", "{\"status\": \"два слова\"}", "[]"})
    void orderWithoutUsableStatus_isAFailureOfThisOrderState(String data) {
        assertThatThrownBy(() -> read(data))
                .isInstanceOfSatisfying(OrderingOrderFailureException.class,
                        failure -> assertThat(failure.operation()).isEqualTo(OrderingOperation.ORDER_STATE));
    }

    private static OrderingOrder read(String data) {
        return OrderingOrders.read(JSON.readTree(data));
    }
}
