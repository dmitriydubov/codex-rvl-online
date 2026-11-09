package ru.projectx.contracts.entity.payment;

import java.util.UUID;

public record PaymentOrderKeys(UUID contractId, UUID correlationId) {
}
