package ru.projectx.contracts.service.km;

import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.party.PersonOwner;
import ru.projectx.contracts.message.KmContractEvents;
import ru.projectx.contracts.service.IssueErrors;
import ru.projectx.contracts.service.usk.IssuanceKind;

final class KmContractKind {

    static final IssuanceKind<Contract, ContractStatus> KIND = new IssuanceKind<>(KmContractTexts.WORDING, "CONTRACT",
            KmContractEvents.KM_CONTRACT_SUBMITTED, KmContractEvents.KM_CONTRACT_REGISTERED, ContractStatus.PAID,
            ContractStatus.SUBMITTED, IssueErrors.kmContractSubmission(), PersonOwner::contract, Contract::markIssued);

    private KmContractKind() {
    }
}
