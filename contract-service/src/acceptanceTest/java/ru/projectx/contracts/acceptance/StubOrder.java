package ru.projectx.contracts.acceptance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

final class StubOrder {

    static final String NEW = "NEW";
    static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    static final String CANCELED = "CANCELED";

    private static final Set<String> STATUSES = Set.of(NEW, "READY_TO_PAYMENT", PENDING_PAYMENT, "PAID",
            "PAYMENT_OVERDUE", "PAYMENT_ERROR", CANCELED);

    private final String orderId;
    private final JsonNode request;
    private final Instant created;
    private final List<HistoryEvent> history = new CopyOnWriteArrayList<>();
    private volatile String status = NEW;
    private volatile Optional<String> invoiceId = Optional.empty();

    record HistoryEvent(String eventType, String orderStatus, String created) {
    }

    StubOrder(String orderId, JsonNode request, Instant created) {
        this.orderId = orderId;
        this.request = request.deepCopy();
        this.created = created;
    }

    String orderId() {
        return orderId;
    }

    String clientId() {
        return request.path("clientId").asString("");
    }

    String channel() {
        return request.path("eventDetails").path("channel").asString("");
    }

    String documentId() {
        return request.path("orderDetails").path(0).path("documentId").asString("");
    }

    ObjectNode document() {
        ObjectNode document = (ObjectNode) request.path("orderDetails").path(0).deepCopy();
        document.put("status", status);
        return document;
    }

    Instant created() {
        return created;
    }

    String status() {
        return status;
    }

    Optional<String> invoiceId() {
        return invoiceId;
    }

    List<HistoryEvent> history() {
        return List.copyOf(history);
    }

    void enter(String next) {
        if (!STATUSES.contains(next)) {
            throw new IllegalArgumentException("в статусной модели Ordering нет статуса «" + next + "»");
        }
        this.status = next;
    }

    void invoice(String invoice) {
        this.invoiceId = Optional.of(invoice);
        this.status = PENDING_PAYMENT;
    }

    void remember(String eventType, String moment) {
        history.add(new HistoryEvent(eventType, status, moment));
    }
}
