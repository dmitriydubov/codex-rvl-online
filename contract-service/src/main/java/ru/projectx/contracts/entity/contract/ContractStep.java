package ru.projectx.contracts.entity.contract;

import java.util.UUID;

public enum ContractStep {
    OFFER_ACCEPTED(ContractStatus.DRAFT_READY, ContractStatus.ACCEPTED),
    INVOICE_ISSUED(ContractStatus.ACCEPTED, ContractStatus.INVOICE_ISSUED);

    private final ContractStatus from;
    private final ContractStatus to;

    ContractStep(ContractStatus from, ContractStatus to) {
        this.from = from;
        this.to = to;
    }

    ContractStatus after(ContractStatus current, UUID contractId) {
        return current.next(from, to, contractId);
    }
}
