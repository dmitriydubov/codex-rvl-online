package ru.projectx.contracts.service.payment.event;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.payment.PaymentOrderKeys;
import ru.projectx.contracts.entity.payment.PaymentOrderView;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.service.payment.IgnoredEvent;

@Component
class OrderEventMatching {

    static final String NO_EVENT_TYPE = "в событии нет eventType";
    static final String NO_ORDER_ID = "в событии нет orderId";
    static final String UNKNOWN_ORDER = "заказа с таким orderId у Договоров нет";
    static final String DOCUMENT_MISMATCH = "документ события не совпадает с документом заказа";
    static final String AMOUNT_MISMATCH = "сумма документа в событии не равна премии договора";

    private final PaymentOrderRepository orders;

    OrderEventMatching(PaymentOrderRepository orders) {
        this.orders = orders;
    }

    Optional<Ignored> unusable(OrderEvent event) {
        if (event.type().isEmpty()) {
            return Optional.of(new Ignored(IgnoredEvent.NOT_APPLICABLE, NO_EVENT_TYPE));
        }
        if (event.document() instanceof EventDocument.Foreign foreign) {
            return Optional.of(new Ignored(IgnoredEvent.FOREIGN_DOCUMENT, foreign.reason()));
        }
        return withoutOrder(event);
    }

    private static Optional<Ignored> withoutOrder(OrderEvent event) {
        return event.orderId().isEmpty()
                ? Optional.of(new Ignored(IgnoredEvent.UNKNOWN_ORDER, NO_ORDER_ID)) : Optional.empty();
    }

    Optional<PaymentOrderKeys> orderOf(String orderId, UUID documentId) {
        return orders.findKeysByExternalOrderId(orderId).or(() -> orders.findKeysById(documentId));
    }

    Optional<Ignored> mismatch(PaymentOrderView order, String orderId, EventDocument.Ours document) {
        if (order.orderId().filter(known -> !known.equals(orderId)).isPresent()) {
            return Optional.of(new Ignored(IgnoredEvent.UNKNOWN_ORDER, UNKNOWN_ORDER));
        }
        if (!order.id().equals(document.documentId())) {
            return Optional.of(new Ignored(IgnoredEvent.DOCUMENT_MISMATCH, DOCUMENT_MISMATCH));
        }
        return amountMismatch(order, document);
    }

    private static Optional<Ignored> amountMismatch(PaymentOrderView order, EventDocument.Ours document) {
        boolean sameAmount = document.premium()
                .filter(premium -> premium.compareTo(order.document().amount()) == 0)
                .isPresent();
        return sameAmount ? Optional.empty() : Optional.of(new Ignored(IgnoredEvent.AMOUNT_MISMATCH, AMOUNT_MISMATCH));
    }
}
