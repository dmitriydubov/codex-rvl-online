package ru.projectx.contracts.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.entity.CancelReason;
import ru.projectx.contracts.entity.Cancellation;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.entity.DocumentKind;
import ru.projectx.contracts.entity.IssuanceRecord;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.entity.RequestParticipant;
import ru.projectx.contracts.message.ContractTask;
import ru.projectx.contracts.message.TaskParticipant;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.repository.LmPolicyRepository;

@Service
public class IssuanceService {

    static final String EXCLUDED_FROM_TRIP = "участник исключён из состава поездки";
    static final String KIND_CHANGED = "вид документа по участнику изменён";

    private static final Set<LmPolicyStatus> STOPPED_POLICIES = Set.of(LmPolicyStatus.ERROR, LmPolicyStatus.CANCELLED);

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

    public List<RequestParticipant> apply(ContractTask task, Instant now) {
        RequestIssuance current = active(task.individualRequestId());
        List<RequestParticipant> composition = new ArrayList<>();
        for (TaskParticipant participant : task.participants()) {
            composition.add(new RequestParticipant(participant.id(), reconcile(task, participant, current, now)));
            current.forget(participant.id());
        }
        for (ActiveIssuance leftover : current.leftovers()) {
            exclude(leftover, task, now);
        }
        return composition;
    }

    private RequestIssuance active(UUID individualRequestId) {
        List<Contract> activeContracts = contracts.findRunningOrFailedAfterPayment(individualRequestId,
                ContractStatus.ERROR, ContractStatus.CANCELLED);
        contracts.lockFresh(activeContracts);
        List<LmPolicy> activePolicies =
                policies.findByRequestIndividualRequestIdAndStatusNotIn(individualRequestId, STOPPED_POLICIES);
        policies.lockFresh(activePolicies);
        return new RequestIssuance(stillHeld(activeContracts), stillActive(activePolicies));
    }

    private static List<Contract> stillHeld(List<Contract> locked) {
        return locked.stream()
                .filter(contract -> !contract.status().stopped() || PaidContracts.failedAfterPayment(contract))
                .toList();
    }

    private static <R extends IssuanceRecord> List<R> stillActive(List<R> locked) {
        return locked.stream().filter(record -> !record.status().stopped()).toList();
    }

    private DocumentKind reconcile(ContractTask task, TaskParticipant participant, RequestIssuance current,
            Instant now) {
        DocumentKind kind = DocumentKinds.of(participant);
        Optional<ActiveIssuance> replaced = current.of(participant.id()).filter(active -> active.kind() != kind);
        Optional<ActiveIssuance> issued = replaced.filter(ActiveIssuance::issued);
        if (issued.isPresent()) {
            IssuedRecords.keepOnKindChange(issued.get(), task);
            return issued.get().kind();
        }
        replaced.ifPresent(active -> cancel(active.record(), KIND_CHANGED, now));
        issue(kind, new Issue(task, participant), current, now);
        return kind;
    }

    private void issue(DocumentKind kind, Issue issue, RequestIssuance current, Instant now) {
        UUID participantId = issue.participant().id();
        switch (kind) {
            case KM_CONTRACT -> kmContracts.reconcile(issue, current.contractOf(participantId), now);
            case LM_POLICY -> lmPolicies.reconcile(issue, current.policyOf(participantId), now);
            default -> { }
        }
    }

    private static void exclude(ActiveIssuance leftover, ContractTask task, Instant now) {
        if (leftover.issued()) {
            IssuedRecords.keepOnExclusion(leftover, task);
            return;
        }
        cancel(leftover.record(), EXCLUDED_FROM_TRIP, now);
    }

    private static void cancel(IssuanceRecord record, String comment, Instant now) {
        record.cancel(new Cancellation(CancelReason.OTHER, comment, now), now);
    }
}
