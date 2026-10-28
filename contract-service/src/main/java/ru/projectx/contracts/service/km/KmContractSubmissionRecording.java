package ru.projectx.contracts.service.km;

import java.time.Clock;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.service.leadership.LeadershipFence;
import ru.projectx.contracts.service.outbox.OutboxJournal;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.service.usk.UskRequestRecording;

@Service
public class KmContractSubmissionRecording extends UskRequestRecording<Contract, ContractStatus, SubmissionFacts> {

    public KmContractSubmissionRecording(ContractRepository contracts, KmContractSubmissionFacts facts,
            KmContractSubmissionMessages messages, OutboxJournal journal, RequestStatusPublication statuses,
            LeadershipFence fence, Clock clock) {
        super(KmContractKind.KIND, contracts, facts, messages, journal, statuses, fence, clock);
    }
}
