package ru.projectx.contracts.service.km;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.config.KmContractSubmissionProperties;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;
import ru.projectx.contracts.service.reference.IssuanceReferences;
import ru.projectx.contracts.service.reference.ReferenceMetrics;
import ru.projectx.contracts.service.usk.UskPreparationMetrics;
import ru.projectx.contracts.service.usk.UskRequests;

@Service
public class KmContractSubmissions extends UskRequests<Contract, ContractStatus> {

    public KmContractSubmissions(ContractRepository contracts, KmContractSubmissionRecording recording,
            IssuanceReferences references, ReferenceMetrics metrics, UskPreparationMetrics preparationMetrics,
            KmContractCertificates certificates, KmContractSubmissionProperties properties,
            BackgroundLeadership leadership) {
        super(KmContractKind.KIND, contracts, recording, certificates, references, metrics, preparationMetrics,
                properties.batchSize(), leadership, LoggerFactory.getLogger(KmContractSubmissions.class));
    }
}
