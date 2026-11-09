package ru.projectx.contracts.service.payment;

import java.util.UUID;
import ru.projectx.contracts.entity.payment.OrderDocument;

public record NewOrder(UUID clientId, UUID documentId, OrderDocument document) {
}
