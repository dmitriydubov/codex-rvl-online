package ru.projectx.contracts.service;

import ru.projectx.contracts.entity.DocumentKind;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.entity.CancelReason;
import ru.projectx.contracts.entity.Cancellation;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.entity.IssuanceRecord;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.message.ContractTask;
import ru.projectx.contracts.message.TaskParticipant;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.repository.LmPolicyRepository;

@Service
public class IssuanceService {

    static final String EXCLUDED_FROM_TRIP = "участник исключён из состава поездки";
    static final String KIND_CHANGED = "вид документа по участнику изменён";

    private static final Set<ContractStatus> FINISHED_CONTRACTS =
            Set.of(ContractStatus.ISSUED, ContractStatus.ERROR, ContractStatus.CANCELLED);
    private static final Set<LmPolicyStatus> FINISHED_POLICIES =
            Set.of(LmPolicyStatus.ISSUED, LmPolicyStatus.ERROR, LmPolicyStatus.CANCELLED);

    private final ContractRepository contracts;
    private final LmPolicyRepository policies;
    private final KmContractIssuance kmContracts;
    private final LmPolicyIssuance lmPolicies;

    public IssuanceService(ContractRepository contracts, LmPolicyRepository policies,
            KmContractIssuance kmContracts, LmPolicyIssuance lmPolicies) {
        this.contracts = contracts;
        this.policies = policies;
        this.kmContracts = kmContracts;
        this.lmPolicies = lmPolicies;
    }

    public void apply(ContractTask task, Instant now) {
        RequestIssuance current = active(task.individualRequestId());
        for (TaskParticipant participant : task.participants()) {
            reconcile(task, participant, current, now);
        }
        current.leftovers().forEach(record -> cancel(record, EXCLUDED_FROM_TRIP, now));
    }

    private RequestIssuance active(UUID individualRequestId) {
        return new RequestIssuance(
                contracts.findByRequestIndividualRequestIdAndStatusNotIn(individualRequestId, FINISHED_CONTRACTS),
                policies.findByRequestIndividualRequestIdAndStatusNotIn(individualRequestId, FINISHED_POLICIES));
    }

    private void reconcile(ContractTask task, TaskParticipant participant, RequestIssuance current, Instant now) {
        DocumentKind kind = DocumentKinds.of(participant);
        current.of(participant.id())
                .filter(active -> active.kind() != kind)
                .ifPresent(active -> cancel(active.record(), KIND_CHANGED, now));
        Issue issue = new Issue(task, participant);
        switch (kind) {
            case KM_CONTRACT -> kmContracts.reconcile(issue, current.contractOf(participant.id()), now);
            case LM_POLICY -> lmPolicies.reconcile(issue, current.policyOf(participant.id()), now);
            default -> { }
        }
        current.forget(participant.id());
    }

    private static void cancel(IssuanceRecord record, String comment, Instant now) {
        record.cancel(new Cancellation(CancelReason.OTHER, comment, now), now);
    }
}
