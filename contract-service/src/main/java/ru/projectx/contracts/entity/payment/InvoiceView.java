package ru.projectx.contracts.entity.payment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import ru.projectx.contracts.entity.issuance.Cancellation;

public record InvoiceView(
        UUID orderId,
        UUID contractId,
        InvoiceState state,
        int fixRepeats,
        Optional<InvoiceTerm> term,
        Optional<InvoiceLink> link,
        Optional<Instant> paidAt,
        Optional<String> rejectionCode,
        Optional<Cancellation> deferredCancellation) {

    public boolean awaitsPayment() {
        return state.awaitsPayment();
    }

    public boolean lacksPaymentLink() {
        return awaitsPayment() && link.isEmpty();
    }

    public boolean cancellationDeferred() {
        return deferredCancellation.isPresent();
    }

    public boolean checkoutStartedBy(Instant moment) {
        return term.map(InvoiceTerm::startedAt).filter(started -> !started.isAfter(moment)).isPresent();
    }
}
