package ru.projectx.contracts.service.payment;

import java.util.Optional;
import java.util.UUID;

public interface OrderingSession {

    Optional<String> findOrder(UUID clientId, UUID documentId);

    String createOrder(NewOrder order);

    String orderStatus(String orderId);

    void cancelOrder(String orderId);
}
