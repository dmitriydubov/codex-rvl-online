package ru.projectx.contracts.service.payment;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record OrderingOrder(String status, List<OrderingEvent> events) {

    public OrderingOrder {
        events = List.copyOf(events);
    }

    public boolean in(String expected) {
        return expected.equals(status);
    }

    public Optional<Instant> lastMoment(String eventType) {
        return events.stream()
                .filter(event -> eventType.equals(event.eventType()))
                .map(OrderingEvent::created)
                .max(Comparator.naturalOrder());
    }
}
