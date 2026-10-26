package ru.projectx.contracts.service.km;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.message.MessageMeta;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.service.outbox.OutboxJournal;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.service.usk.UskRequestRecording;
import ru.projectx.contracts.service.usk.UskSubmission;

@Service
public class KmContractSubmissionRecording extends UskRequestRecording<Contract, ContractStatus> {

    private final KmContractSubmissionFacts facts;
    private final KmContractSubmissionMessages messages;
    private final KmContractMessageLimit limit;

    public KmContractSubmissionRecording(ContractRepository contracts, KmContractSubmissionFacts facts,
            KmContractSubmissionMessages messages, KmContractMessageLimit limit, OutboxJournal journal,
            RequestStatusPublication statuses, Clock clock) {
        super(KmContractKind.KIND, contracts, journal, statuses, clock);
        this.facts = facts;
        this.messages = messages;
        this.limit = limit;
    }

    @Override
    protected UskSubmission compose(Contract contract, ExternalIdentifiers identifiers, Instant now) {
        SubmissionFacts gathered = facts.gather(contract, identifiers);
        MessageMeta meta = messages.meta(contract, now);
        String payload = messages.write(meta, contract, gathered);
        limit.verify(payload, gathered.certificates());
        return new UskSubmission(draft(contract, meta, payload), gathered.certificates());
    }
}
