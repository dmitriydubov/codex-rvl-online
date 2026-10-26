package ru.projectx.contracts.service.km;

import java.time.Clock;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.config.KmContractSubmissionProperties;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.service.usk.UskAwaiting;

@Service
public class KmContractAwaiting extends UskAwaiting<Contract, ContractStatus> {

    public KmContractAwaiting(ContractRepository contracts, KmContractAwaitingMetrics metrics,
            KmContractSubmissionProperties properties, Clock clock) {
        super(KmContractKind.KIND, contracts, metrics, properties.awaitingThreshold(), clock);
    }
}
