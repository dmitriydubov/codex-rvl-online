package ru.projectx.contracts.service.payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import ru.projectx.contracts.entity.payment.InvoiceLink;

final class ScriptedOrdering implements Ordering {

    static final String PAYMENT_URL = "https://pay.example.test/pay/";

    private static final OrderingOrder NEW_ORDER = new OrderingOrder(OrderingStatuses.NEW, List.of());
    private static final Runnable NOTHING = () -> { };

    private final ConcurrentMap<UUID, String> ordersByDocument = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, OrderingOrder> states = new ConcurrentHashMap<>();
    private final AtomicInteger creations = new AtomicInteger();
    private final AtomicInteger checkouts = new AtomicInteger();
    private final AtomicInteger stateRequests = new AtomicInteger();
    private final List<String> cancelled = new CopyOnWriteArrayList<>();
    private volatile Runnable duringCreation = NOTHING;
    private volatile Runnable duringSearch = NOTHING;
    private volatile Runnable duringCheckout = NOTHING;
    private volatile Runnable duringStateRequest = NOTHING;

    void reset() {
        ordersByDocument.clear();
        states.clear();
        creations.set(0);
        checkouts.set(0);
        stateRequests.set(0);
        cancelled.clear();
        duringCreation = NOTHING;
        duringSearch = NOTHING;
        duringCheckout = NOTHING;
        duringStateRequest = NOTHING;
    }

    void duringCreation(Runnable action) {
        this.duringCreation = action;
    }

    void duringSearch(Runnable action) {
        this.duringSearch = action;
    }

    void duringCheckout(Runnable action) {
        this.duringCheckout = action;
    }

    void duringStateRequest(Runnable action) {
        this.duringStateRequest = action;
    }

    void orderIs(String orderId, OrderingOrder state) {
        states.put(orderId, state);
    }

    int creations() {
        return creations.get();
    }

    int checkouts() {
        return checkouts.get();
    }

    int stateRequests() {
        return stateRequests.get();
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
                return states.getOrDefault(orderId, NEW_ORDER).status();
            }

            @Override
            public void cancelOrder(String orderId) {
                cancelled.add(orderId);
                ordersByDocument.values().remove(orderId);
            }

            @Override
            public InvoiceLink checkout(CheckoutRequest request) {
                String invoiceId = "invoice-" + checkouts.incrementAndGet();
                states.put(request.orderId(), new OrderingOrder(OrderingStatuses.PENDING_PAYMENT, List.of()));
                duringCheckout.run();
                return new InvoiceLink(invoiceId, PAYMENT_URL + invoiceId);
            }

            @Override
            public OrderingOrder order(String orderId) {
                stateRequests.incrementAndGet();
                OrderingOrder state = states.getOrDefault(orderId, NEW_ORDER);
                duringStateRequest.run();
                return state;
            }
        };
    }
}
