package ru.projectx.contracts.service.km;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.service.usk.UndeliveredUskRequests;

@Component
public class UndeliveredKmContractSubmissions extends UndeliveredUskRequests<Contract, ContractStatus> {

    public UndeliveredKmContractSubmissions(ContractRepository contracts, RequestStatusPublication statuses) {
        super(KmContractKind.KIND, contracts, statuses, LoggerFactory.getLogger(UndeliveredKmContractSubmissions.class));
    }
}
