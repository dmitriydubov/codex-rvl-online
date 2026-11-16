package ru.projectx.contracts.service.packages;

import ru.projectx.contracts.dto.DocumentState;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.policy.LmPolicyStatus;
import ru.projectx.contracts.service.status.PaymentOffer;

final class PackageStates {

    private PackageStates() {
    }

    static DocumentState shown(ContractStatus status, PaymentOffer offer) {
        return offer.cancellationDeferred() ? DocumentState.CANCELLED : of(status);
    }

    static DocumentState of(ContractStatus status) {
        return switch (status) {
            case NEW -> DocumentState.NOT_REQUIRED;
            case DRAFT_READY -> DocumentState.DRAFTED;
            case ACCEPTED -> DocumentState.ACCEPTED;
            case INVOICE_ISSUED -> DocumentState.INVOICE_ISSUED;
            case PAID, SUBMITTED -> DocumentState.PAID;
            case ISSUED -> DocumentState.ISSUED;
            case ERROR -> DocumentState.ERROR;
            case CANCELLED -> DocumentState.CANCELLED;
        };
    }

    static DocumentState of(LmPolicyStatus status) {
        return switch (status) {
            case NEW, SUBMITTED -> DocumentState.NOT_REQUIRED;
            case ISSUED -> DocumentState.ISSUED;
            case ERROR -> DocumentState.ERROR;
            case CANCELLED -> DocumentState.CANCELLED;
        };
    }
}
