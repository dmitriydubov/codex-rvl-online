package ru.projectx.contracts.service.payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

final class ScriptedOrdering implements Ordering {

    private final ConcurrentMap<UUID, String> ordersByDocument = new ConcurrentHashMap<>();
    private final AtomicInteger creations = new AtomicInteger();
    private final List<String> cancelled = new CopyOnWriteArrayList<>();
    private volatile Runnable duringCreation = () -> { };
    private volatile Runnable duringSearch = () -> { };

    void reset() {
        ordersByDocument.clear();
        creations.set(0);
        cancelled.clear();
        duringCreation = () -> { };
        duringSearch = () -> { };
    }

    void duringCreation(Runnable action) {
        this.duringCreation = action;
    }

    void duringSearch(Runnable action) {
        this.duringSearch = action;
    }

    int creations() {
        return creations.get();
    }

    List<String> cancelled() {
        return List.copyOf(cancelled);
    }

    @Override
    public OrderingSession session(UUID correlationId) {
        return new OrderingSession() {
            @Override
            public Optional<String> findOrder(UUID clientId, UUID documentId) {
                Optional<String> found = Optional.ofNullable(ordersByDocument.get(documentId));
                duringSearch.run();
                return found;
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
                cancelled.add(orderId);
                ordersByDocument.values().remove(orderId);
            }
        };
    }
}
