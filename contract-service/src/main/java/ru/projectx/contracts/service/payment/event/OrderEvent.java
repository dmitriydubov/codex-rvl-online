package ru.projectx.contracts.service.payment.event;

import java.time.Instant;
import java.util.Optional;

public record OrderEvent(Optional<String> type, Optional<String> orderId, EventDocument document,
        Optional<Instant> updated) {
}
