package ru.projectx.contracts.acceptance;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

final class StubOrderViews {

    private static final ZoneOffset MOSCOW = ZoneOffset.ofHours(3);
    private static final DateTimeFormatter WITH_MICROSECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx");

    private final JsonMapper json;

    StubOrderViews(JsonMapper json) {
        this.json = json;
    }

    static String moment(Instant instant) {
        return WITH_MICROSECONDS.format(instant.atOffset(MOSCOW));
    }

    ObjectNode found(StubOrder order) {
        ObjectNode node = json.createObjectNode()
                .put("clientId", order.clientId())
                .put("orderId", order.orderId())
                .put("status", order.status());
        node.putArray("orderDetails").add(order.document());
        return node;
    }

    ObjectNode searchResult(List<StubOrder> orders, boolean asList) {
        ObjectNode response = json.createObjectNode().put("status", "success");
        if (asList) {
            ArrayNode list = response.putArray("data");
            orders.forEach(order -> list.add(found(order)));
        }
        else if (!orders.isEmpty()) {
            response.set("data", found(orders.getFirst()));
        }
        return response;
    }

    ObjectNode state(StubOrder order) {
        ObjectNode node = found(order);
        node.put("ttl", order.document().path("ttl").asString(""));
        node.put("created", moment(order.created()));
        ArrayNode events = node.putArray("events");
        order.history().forEach(event -> events.addObject()
                .put("eventType", event.eventType())
                .put("orderStatus", event.orderStatus())
                .put("created", event.created()));
        return node;
    }

    ObjectNode event(StubOrder order, Instant updated) {
        ObjectNode event = json.createObjectNode()
                .put("orderId", order.orderId())
                .put("orderStatus", order.status())
                .put("channel", order.channel())
                .put("clientId", order.clientId())
                .put("created", moment(order.created()))
                .put("updated", moment(updated))
                .put("ttl", order.document().path("ttl").asString(""));
        event.putArray("orderDetails").add(order.document());
        order.invoiceId().ifPresent(invoiceId -> event.putObject("payment")
                .put("invoiceId", invoiceId)
                .put("status", order.status()));
        return event;
    }
}
