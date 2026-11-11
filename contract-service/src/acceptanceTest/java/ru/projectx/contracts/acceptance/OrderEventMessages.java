package ru.projectx.contracts.acceptance;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class OrderEventMessages {

    private static final String ABSENT = "нет";
    private static final String EVENT_ID = "eventId";
    private static final String EVENT_TYPE = "eventType";
    private static final String ORDER_ID = "orderId";
    private static final String UPDATED = "updated";
    private static final String DOCUMENT_ID = "orderDetails[0].documentId";
    private static final String FOREIGN_ORDER = "0196f0a2-5b1c-7d3e-9f40-1a2b3c4d5eff";
    private static final String FOREIGN_DOCUMENT = "0196f0a2-5b1c-7d3e-9f40-1a2b3c4d5efe";
    private static final String LOST_EVENT_TYPE = "paymentOverdue";
    private static final Map<String, String> STATUS_AFTER = Map.of(
            "fixed", "READY_TO_PAYMENT",
            "pendingPayment", "PENDING_PAYMENT",
            "paid", "PAID",
            "paymentError", "PAYMENT_ERROR",
            "paymentOverdue", "PAYMENT_OVERDUE",
            "canceled", "CANCELED");
    private static final Map<String, Consumer<ObjectNode>> DIFFERENCES = Map.of(
            "чужой orderId", event -> event.put(ORDER_ID, FOREIGN_ORDER),
            "нет orderId", event -> event.remove(ORDER_ID),
            "нет eventType", event -> event.remove(EVENT_TYPE),
            "чужой documentId", event -> set(event, DOCUMENT_ID, FOREIGN_DOCUMENT),
            "другая сумма", event -> document(event).put("documentPremium", new java.math.BigDecimal("33999.99")),
            "документ другого продукта", event -> document(event).put("product", "dmsfl"),
            "documentId не GUID", event -> set(event, DOCUMENT_ID, "11"));
    private static final Map<String, Consumer<ObjectNode>> DEFECTS = Map.of(
            "тело не JSON", event -> { },
            "нет eventId", event -> event.remove(EVENT_ID));

    private final OrderingStub stub;
    private final StandClock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    public OrderEventMessages(OrderingStub stub, StandClock clock) {
        this.stub = stub;
        this.clock = clock;
    }

    ObjectNode event(String eventId, String type, String orderId) {
        StubOrder order = stub.order(orderId)
                .orElseThrow(() -> new IllegalArgumentException("у заглушки Ordering нет заказа " + orderId));
        Optional.ofNullable(STATUS_AFTER.get(type)).ifPresent(order::enter);
        ObjectNode event = json.createObjectNode().put(EVENT_ID, eventId).put(EVENT_TYPE, type);
        event.setAll(stub.views().event(order, clock.instant()));
        return event;
    }

    void rememberInHistory(String orderId, String type, ObjectNode event) {
        stub.order(orderId).ifPresent(order -> order.remember(type, event.path(UPDATED).asString("")));
    }

    static void differ(ObjectNode event, String difference) {
        Optional.ofNullable(DIFFERENCES.get(difference))
                .orElseThrow(() -> new IllegalArgumentException("стенд не знает отличия события «" + difference + "»"))
                .accept(event);
    }

    byte[] defective(String defect) {
        StubOrder order = stub.onlyOrder();
        ObjectNode event = json.createObjectNode().put(EVENT_ID, "d1a0c001-5e2f-4a3b-8c4d-0000000000ff")
                .put(EVENT_TYPE, LOST_EVENT_TYPE);
        event.setAll(stub.views().event(order, clock.instant()));
        Optional.ofNullable(DEFECTS.get(defect))
                .orElseThrow(() -> new IllegalArgumentException("стенд не знает дефекта события «" + defect + "»"))
                .accept(event);
        String text = "тело не JSON".equals(defect) ? "<не JSON> " + event : event.toString();
        return text.getBytes(StandardCharsets.UTF_8);
    }

    static void set(ObjectNode event, String path, String value) {
        int lastDot = path.lastIndexOf('.');
        ObjectNode parent = parent(event, path, lastDot);
        String field = path.substring(lastDot + 1);
        if (ABSENT.equals(value)) {
            parent.remove(field);
            return;
        }
        parent.put(field, value);
    }

    private static ObjectNode parent(ObjectNode event, String path, int lastDot) {
        JsonNode parent = lastDot < 0 ? event : at(event, path.substring(0, lastDot));
        if (parent instanceof ObjectNode object) {
            return object;
        }
        throw new IllegalArgumentException("в событии нет объекта для поля «" + path + "»");
    }

    byte[] bytes(ObjectNode event) {
        return json.writeValueAsBytes(event);
    }

    private static ObjectNode document(ObjectNode event) {
        return (ObjectNode) event.path("orderDetails").path(0);
    }

    private static JsonNode at(JsonNode root, String path) {
        JsonNode node = root;
        for (String part : path.split("\\.")) {
            int index = part.indexOf('[');
            node = index < 0 ? node.path(part)
                    : node.path(part.substring(0, index)).path(Integer.parseInt(part.substring(index + 1,
                            part.length() - 1)));
        }
        return node;
    }
}
