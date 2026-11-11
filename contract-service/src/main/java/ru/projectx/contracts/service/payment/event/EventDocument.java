package ru.projectx.contracts.service.payment.event;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public sealed interface EventDocument {

    record Ours(UUID documentId, Optional<BigDecimal> premium) implements EventDocument {
    }

    record Foreign(String reason) implements EventDocument {
    }
}
