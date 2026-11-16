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
        OrderRequests requests,
        Optional<PayerEmail> payer) {

    public boolean creationRequested() {
        return requests.creation();
    }

    public boolean cancelRequested() {
        return requests.cancellation();
    }

    public boolean mayExistInOrdering() {
        return orderId.isPresent() || requests.creation();
    }
}
