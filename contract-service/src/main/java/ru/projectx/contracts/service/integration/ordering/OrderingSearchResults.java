package ru.projectx.contracts.service.integration.ordering;

import java.util.Optional;
import java.util.stream.Stream;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingOrderFailureException;
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
                .findFirst()
                .map(order -> OrderingReplies.requiredText(OrderingOperation.FIND_ORDERS, order.path(ORDER_ID)));
    }

    private static Stream<JsonNode> orders(JsonNode data) {
        return data.isArray() ? data.valueStream() : single(data).stream();
    }

    private static Optional<JsonNode> single(JsonNode data) {
        if (data.isObject()) {
            return Optional.of(data);
        }
        if (absent(data)) {
            return Optional.empty();
        }
        throw new OrderingOrderFailureException(OrderingOperation.FIND_ORDERS, "список заказов не по формату");
    }

    private static boolean absent(JsonNode data) {
        return data.isMissingNode() || data.isNull();
    }

    private static boolean holds(JsonNode order, String product, String documentId) {
        return order.path(ORDER_DETAILS).valueStream()
                .anyMatch(document -> product.equals(document.path(PRODUCT).asString(""))
                        && documentId.equals(document.path(DOCUMENT_ID).asString("")));
    }
}
