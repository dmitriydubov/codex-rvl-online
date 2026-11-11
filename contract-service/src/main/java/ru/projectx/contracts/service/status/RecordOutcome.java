package ru.projectx.contracts.service.status;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.issuance.IssuanceRecord;
import ru.projectx.contracts.entity.issuance.IssuanceStatus;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.payment.InvoiceView;

record RecordOutcome(UUID recordId, UUID participantId, IssuanceStatus status, @Nullable IssueError error,
        PaymentOffer offer) {

    static RecordOutcome of(IssuanceRecord record) {
        return of(record, Optional.empty());
    }

    static RecordOutcome of(IssuanceRecord record, Optional<InvoiceView> invoice) {
        PaymentOffer offer = PaymentOffer.of(record.status(), invoice);
        IssuanceStatus shown = offer.cancellationDeferred() ? ContractStatus.CANCELLED : record.status();
        return new RecordOutcome(record.id(), record.participantId(), shown, record.state().error().orElse(null),
                offer);
    }

    boolean stopped() {
        return status.stopped();
    }

    boolean linked() {
        return offer.link().isPresent();
    }

    boolean resumedSince(@Nullable RecordOutcome earlier) {
        return earlier != null && earlier.offer.cancellationDeferred() && awaitsPaymentAgain();
    }

    boolean looksLike(@Nullable RecordOutcome earlier) {
        return earlier != null && sameOutcomeAs(earlier) && linked() == earlier.linked();
    }

    private boolean awaitsPaymentAgain() {
        return !offer.cancellationDeferred() && !stopped();
    }

    private boolean sameOutcomeAs(RecordOutcome earlier) {
        return status.equals(earlier.status) && Objects.equals(error, earlier.error);
    }
}
