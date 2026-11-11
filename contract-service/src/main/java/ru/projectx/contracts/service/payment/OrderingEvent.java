package ru.projectx.contracts.service.payment;

import java.time.Instant;

public record OrderingEvent(String eventType, Instant created) {
}
