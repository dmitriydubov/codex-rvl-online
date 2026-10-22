package ru.projectx.contracts.exception;

import java.util.UUID;

public class ContractAlreadyAcceptedException extends AcceptanceRefusedException {

    private static final long serialVersionUID = 1L;

    public ContractAlreadyAcceptedException(UUID contractId, int acceptedVersion) {
        super(AcceptanceRefusal.ALREADY_ACCEPTED,
                "договор КМ " + contractId + " уже акцептован по версии " + acceptedVersion);
    }
}
