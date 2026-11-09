package ru.projectx.contracts.service.payment;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

final class ScriptedOrdering implements Ordering {

    private final ConcurrentMap<UUID, String> ordersByDocument = new ConcurrentHashMap<>();
    private final AtomicInteger creations = new AtomicInteger();
    private volatile Runnable duringCreation = () -> { };

    void reset() {
        ordersByDocument.clear();
        creations.set(0);
        duringCreation = () -> { };
    }

    void duringCreation(Runnable action) {
        this.duringCreation = action;
    }

    int creations() {
        return creations.get();
    }

    @Override
    public OrderingSession session(UUID correlationId) {
        return new OrderingSession() {
            @Override
            public Optional<String> findOrder(UUID clientId, UUID documentId) {
                return Optional.ofNullable(ordersByDocument.get(documentId));
            }

            @Override
            public String createOrder(NewOrder order) {
                String orderId = "order-" + creations.incrementAndGet();
                ordersByDocument.put(order.documentId(), orderId);
                duringCreation.run();
                return orderId;
            }

            @Override
            public String orderStatus(String orderId) {
                return "NEW";
            }

            @Override
            public void cancelOrder(String orderId) {
                ordersByDocument.values().remove(orderId);
            }
        };
    }
}
