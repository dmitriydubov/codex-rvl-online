package ru.projectx.contracts.service.payment;

import java.util.Optional;
import java.util.UUID;
import ru.projectx.contracts.entity.payment.InvoiceLink;

public interface OrderingSession {

    Optional<String> findOrder(UUID clientId, UUID documentId);

    String createOrder(NewOrder order);

    String orderStatus(String orderId);

    void cancelOrder(String orderId);

    InvoiceLink checkout(CheckoutRequest request);

    OrderingOrder order(String orderId);
}
