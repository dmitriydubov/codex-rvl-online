package ru.projectx.contracts.entity.payment;

import java.util.UUID;

public record OrderDraft(UUID contractId, UUID clientId, OrderDocument document, PayerEmail payer) {
}
