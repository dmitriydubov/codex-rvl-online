package ru.projectx.contracts.service.integration.ordering;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.service.payment.OrderingEvent;
import ru.projectx.contracts.service.payment.OrderingOrder;
import tools.jackson.databind.JsonNode;

final class OrderingOrders {

    private static final String STATUS = "status";
    private static final String EVENTS = "events";
    private static final String EVENT_TYPE = "eventType";
    private static final String CREATED = "created";

    private OrderingOrders() {
    }

    static OrderingOrder read(JsonNode data) {
        String status = OrderingReplies.requiredText(OrderingOperation.ORDER_STATE, data.path(STATUS));
        List<OrderingEvent> events = data.path(EVENTS).valueStream()
                .map(OrderingOrders::event)
                .flatMap(Optional::stream)
                .toList();
        return new OrderingOrder(status, events);
    }

    private static Optional<OrderingEvent> event(JsonNode event) {
        String type = event.path(EVENT_TYPE).asString("");
        return moment(event.path(CREATED).asString(""))
                .filter(created -> !type.isEmpty())
                .map(created -> new OrderingEvent(type, created));
    }

    private static Optional<Instant> moment(String text) {
        try {
            return Optional.of(OffsetDateTime.parse(text).toInstant());
        }
        catch (DateTimeException unparsed) {
            return Optional.empty();
        }
    }
}
