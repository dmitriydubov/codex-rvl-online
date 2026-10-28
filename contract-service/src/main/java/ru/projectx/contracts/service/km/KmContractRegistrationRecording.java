package ru.projectx.contracts.service.km;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.service.usk.result.UskResultRecording;

@Service
public class KmContractRegistrationRecording extends UskResultRecording<Contract, ContractStatus> {

    public KmContractRegistrationRecording(ContractRepository contracts, PrintedFormRepository forms,
            RequestStatusPublication statuses) {
        super(KmContractKind.KIND, contracts, forms, statuses,
                LoggerFactory.getLogger(KmContractRegistrationRecording.class));
    }
}
