package ru.projectx.contracts.service.acceptance;

import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractAcceptance;
import ru.projectx.contracts.exception.ContractAlreadyAcceptedException;
import ru.projectx.contracts.exception.DocumentVersionMismatchException;

final class OfferVersions {

    private OfferVersions() {
    }

    static void ensureAccepted(ContractAcceptance previous, int requestedVersion) {
        int acceptedVersion = previous.offer().contractVersion();
        if (acceptedVersion != requestedVersion) {
            throw new ContractAlreadyAcceptedException(previous.contractId(), acceptedVersion);
        }
    }

    static int current(Contract contract, int requestedVersion) {
        int currentVersion = contract.state().entityVersion();
        if (currentVersion != requestedVersion) {
            throw new DocumentVersionMismatchException(contract.id(), currentVersion);
        }
        return currentVersion;
    }
}
