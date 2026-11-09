package ru.projectx.contracts.service.payment;

import java.util.UUID;

public interface Ordering {

    OrderingSession session(UUID correlationId);
}
