package ru.projectx.contracts.service.payment.event;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import ru.projectx.contracts.service.Guids;
import tools.jackson.databind.JsonNode;

public final class OrderEventDocuments {

    static final String NO_DOCUMENT = "в событии нет документа продукта Договоров";
    static final String DOCUMENT_ID_NOT_GUID = "идентификатор документа в событии не в формате GUID";
    private static final String ORDER_DETAILS = "orderDetails";
    private static final String PRODUCT = "product";
    private static final String DOCUMENT_ID = "documentId";
    private static final String DOCUMENT_PREMIUM = "documentPremium";

    private OrderEventDocuments() {
    }

    public static Optional<UUID> ourDocumentId(JsonNode event, String product) {
        return ours(event, product)
                .flatMap(document -> Guids.canonical(document.path(DOCUMENT_ID).asString("")));
    }

    static EventDocument of(JsonNode event, String product) {
        Optional<JsonNode> ours = ours(event, product);
        if (ours.isEmpty()) {
            return new EventDocument.Foreign(NO_DOCUMENT);
        }
        JsonNode document = ours.get();
        return Guids.canonical(document.path(DOCUMENT_ID).asString(""))
                .<EventDocument>map(documentId -> new EventDocument.Ours(documentId, premium(document)))
                .orElseGet(() -> new EventDocument.Foreign(DOCUMENT_ID_NOT_GUID));
    }

    private static Optional<JsonNode> ours(JsonNode event, String product) {
        return event.path(ORDER_DETAILS).valueStream()
                .filter(document -> product.equals(document.path(PRODUCT).asString("")))
                .findFirst();
    }

    private static Optional<BigDecimal> premium(JsonNode document) {
        return Optional.of(document.path(DOCUMENT_PREMIUM))
                .filter(JsonNode::isNumber)
                .map(JsonNode::decimalValue);
    }
}
