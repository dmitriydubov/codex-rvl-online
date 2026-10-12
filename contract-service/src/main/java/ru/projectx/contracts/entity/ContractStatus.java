package ru.projectx.contracts.entity;

import java.util.Set;

public enum ContractStatus implements IssuanceStatus {
    NEW, DRAFT_READY, ACCEPTED, INVOICE_ISSUED, PAID, SUBMITTED, ISSUED, ERROR, CANCELLED;

    private static final Set<ContractStatus> FINISHED = Set.of(ISSUED, ERROR, CANCELLED);

    @Override
    public boolean finished() {
        return FINISHED.contains(this);
    }
}
