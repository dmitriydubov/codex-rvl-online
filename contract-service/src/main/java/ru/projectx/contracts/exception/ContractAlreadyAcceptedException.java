package ru.projectx.contracts.exception;

import java.util.UUID;

public class ContractAlreadyAcceptedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ContractAlreadyAcceptedException(UUID contractId, int acceptedVersion) {
        super("договор КМ " + contractId + " уже акцептован по версии " + acceptedVersion);
    }
}
