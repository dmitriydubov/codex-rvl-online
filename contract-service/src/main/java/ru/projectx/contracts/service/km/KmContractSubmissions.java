package ru.projectx.contracts.service.km;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.config.KmContractSubmissionProperties;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.service.reference.IssuanceReferences;
import ru.projectx.contracts.service.reference.ReferenceMetrics;
import ru.projectx.contracts.service.usk.UskRequests;

@Service
public class KmContractSubmissions extends UskRequests<Contract, ContractStatus> {

    public KmContractSubmissions(ContractRepository contracts, KmContractSubmissionRecording recording,
            IssuanceReferences references, ReferenceMetrics metrics, KmContractSubmissionProperties properties) {
        super(KmContractKind.KIND, contracts, recording, references, metrics, properties.batchSize(),
                LoggerFactory.getLogger(KmContractSubmissions.class));
    }
}
