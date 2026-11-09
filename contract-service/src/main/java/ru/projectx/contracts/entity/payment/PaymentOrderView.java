package ru.projectx.contracts.entity.payment;

import java.util.Optional;
import java.util.UUID;

public record PaymentOrderView(
        UUID id,
        UUID contractId,
        UUID clientId,
        UUID correlationId,
        OrderDocument document,
        PaymentOrderState state,
        Optional<String> orderId,
        Optional<String> rejectionCode,
        boolean creationRequested,
        boolean cancelRequested) {
}
