package ru.projectx.contracts.service.km;

import java.time.Clock;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.service.outbox.OutboxJournal;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.service.usk.UskRequestRecording;

@Service
public class KmContractSubmissionRecording extends UskRequestRecording<Contract, ContractStatus, SubmissionFacts> {

    public KmContractSubmissionRecording(ContractRepository contracts, KmContractSubmissionFacts facts,
            KmContractSubmissionMessages messages, OutboxJournal journal, RequestStatusPublication statuses,
            Clock clock) {
        super(KmContractKind.KIND, contracts, facts, messages, journal, statuses, clock);
    }
}
