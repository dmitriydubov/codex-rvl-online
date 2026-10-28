package ru.projectx.contracts.entity;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import ru.projectx.contracts.entity.issuance.IssuanceOutcome;
import ru.projectx.contracts.entity.issuance.IssuanceStatus;
import ru.projectx.contracts.entity.issuance.IssuanceTransition;

public enum ContractStatus implements IssuanceStatus {
    NEW(IssuanceOutcome.PENDING),
    DRAFT_READY(IssuanceOutcome.PENDING),
    ACCEPTED(IssuanceOutcome.PENDING),
    INVOICE_ISSUED(IssuanceOutcome.PENDING),
    PAID(IssuanceOutcome.PENDING),
    SUBMITTED(IssuanceOutcome.PENDING),
    ISSUED(IssuanceOutcome.ISSUED),
    ERROR(IssuanceOutcome.ERROR),
    CANCELLED(IssuanceOutcome.CANCELLED);

    private static final Set<ContractStatus> OFFER_ACCEPTED = EnumSet.of(ACCEPTED, INVOICE_ISSUED, PAID, SUBMITTED);
    private static final Set<ContractStatus> AWAITING_PAYMENT = EnumSet.of(ACCEPTED, INVOICE_ISSUED);

    private final IssuanceOutcome outcome;

    ContractStatus(IssuanceOutcome outcome) {
        this.outcome = outcome;
    }

    @Override
    public IssuanceOutcome outcome() {
        return outcome;
    }

    public boolean offerAccepted() {
        return OFFER_ACCEPTED.contains(this);
    }

    public boolean awaitsPayment() {
        return AWAITING_PAYMENT.contains(this);
    }

    ContractStatus after(IssuanceTransition transition, UUID contractId) {
        return switch (transition) {
            case SUBMITTED -> next(PAID, SUBMITTED, contractId);
            case ERROR -> ERROR;
            case CANCELLED -> CANCELLED;
        };
    }

    ContractStatus next(ContractStatus expected, ContractStatus next, UUID contractId) {
        if (this != expected) {
            throw new IllegalStateException("договор КМ " + contractId + " в статусе " + this + ", а не " + expected);
        }
        return next;
    }
}
