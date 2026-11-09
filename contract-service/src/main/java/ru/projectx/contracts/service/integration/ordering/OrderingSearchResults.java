package ru.projectx.contracts.service.integration.ordering;

import java.util.Optional;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;

final class OrderingSearchResults {

    private static final String DATA = "data";
    private static final String ORDER_ID = "orderId";
    private static final String ORDER_DETAILS = "orderDetails";
    private static final String PRODUCT = "product";
    private static final String DOCUMENT_ID = "documentId";

    private OrderingSearchResults() {
    }

    static Optional<String> orderId(JsonNode body, String product, String documentId) {
        return orders(body.path(DATA))
                .filter(order -> holds(order, product, documentId))
                .map(order -> order.path(ORDER_ID))
                .filter(JsonNode::isString)
                .map(JsonNode::asString)
                .filter(OrderingReplies::identifier)
                .findFirst();
    }

    private static Stream<JsonNode> orders(JsonNode data) {
        return data.isArray() ? data.valueStream() : Stream.of(data).filter(JsonNode::isObject);
    }

    private static boolean holds(JsonNode order, String product, String documentId) {
        return order.path(ORDER_DETAILS).valueStream()
                .anyMatch(document -> product.equals(document.path(PRODUCT).asString(""))
                        && documentId.equals(document.path(DOCUMENT_ID).asString("")));
    }
}
