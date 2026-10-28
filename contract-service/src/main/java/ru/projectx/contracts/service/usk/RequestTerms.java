package ru.projectx.contracts.service.usk;

import ru.projectx.contracts.entity.issuance.IssuanceState;
import ru.projectx.contracts.message.usk.UskRequestTerms;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;

public final class RequestTerms {

    private RequestTerms() {
    }

    public static UskRequestTerms of(IssuanceState state, ExternalIdentifiers identifiers) {
        return new UskRequestTerms(state.request().individualRequestId(), identifiers.insuranceContractExternalId(),
                identifiers.insurerPolicyExternalId(), identifiers.programExternalId(), state.period().dateFrom(),
                state.period().dateTo());
    }
}
