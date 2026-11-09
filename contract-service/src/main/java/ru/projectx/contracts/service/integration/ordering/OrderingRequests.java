package ru.projectx.contracts.service.integration.ordering;

import java.math.RoundingMode;
import java.time.ZoneId;
import ru.projectx.contracts.config.payment.OrderingProperties;
import ru.projectx.contracts.entity.payment.OrderDocument;
import ru.projectx.contracts.service.payment.NewOrder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class OrderingRequests {

    private static final String DOCUMENT_TYPE = "contract";
    private static final int MONEY_SCALE = 2;

    private final JsonMapper json;
    private final String product;
    private final String channel;
    private final String cancelReason;
    private final ZoneId zone;

    OrderingRequests(JsonMapper json, OrderingProperties settings) {
        this.json = json;
        this.product = settings.product();
        this.channel = settings.channel();
        this.cancelReason = settings.cancelReason();
        this.zone = settings.document().zone();
    }

    String product() {
        return product;
    }

    ObjectNode create(NewOrder order) {
        ObjectNode body = json.createObjectNode();
        body.put("clientId", order.clientId().toString());
        body.putArray("orderDetails").add(document(order));
        body.putObject("eventDetails").put("channel", channel);
        return body;
    }

    ObjectNode find(String documentId) {
        ObjectNode body = json.createObjectNode();
        body.putObject("orderDetails")
                .put("product", product)
                .put("documentId", documentId);
        return body;
    }

    ObjectNode cancel() {
        ObjectNode body = json.createObjectNode();
        body.put("channel", channel);
        body.put("cancelReason", cancelReason);
        return body;
    }

    private ObjectNode document(NewOrder order) {
        OrderDocument document = order.document();
        ObjectNode details = json.createObjectNode();
        details.put("product", product);
        details.put("documentType", DOCUMENT_TYPE);
        details.put("documentId", order.documentId().toString());
        details.put("documentNumber", document.contractNumber());
        details.put("documentPremium", document.amount().setScale(MONEY_SCALE, RoundingMode.UNNECESSARY));
        details.put("documentDate", OrderingDates.format(document.documentDate(), zone));
        details.put("ttl", OrderingDates.format(document.documentTtl(), zone));
        return details;
    }
}
