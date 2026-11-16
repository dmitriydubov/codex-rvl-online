package ru.projectx.contracts.service.payment.event;

import java.time.Instant;
import java.util.Optional;

interface OrderEventReaction {

    String eventType();

    Optional<Ignored> react(EventTarget target, OrderEvent event, Instant now);
}
