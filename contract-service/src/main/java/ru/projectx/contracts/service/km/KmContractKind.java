package ru.projectx.contracts.service.km;

import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.entity.PersonOwner;
import ru.projectx.contracts.message.KmContractEvents;
import ru.projectx.contracts.service.IssueErrors;
import ru.projectx.contracts.service.usk.IssuanceKind;

final class KmContractKind {

    static final IssuanceKind<Contract, ContractStatus> KIND = new IssuanceKind<>(KmContractTexts.WORDING, "CONTRACT",
            KmContractEvents.KM_CONTRACT_REGISTERED, ContractStatus.PAID, ContractStatus.SUBMITTED,
            IssueErrors.kmContractSubmissionUndelivered(), PersonOwner::contract, Contract::markIssued);

    private KmContractKind() {
    }
}
