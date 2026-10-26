package ru.projectx.contracts.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.entity.Cancellation;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.entity.IssuanceRecord;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.message.ContractTaskCancellation;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.repository.LmPolicyRepository;

@Service
public class TaskCancellationService {

    private static final Set<ContractStatus> UNTOUCHED_CONTRACTS = Set.of(ContractStatus.ISSUED, ContractStatus.CANCELLED);
    private static final Set<LmPolicyStatus> UNTOUCHED_POLICIES = Set.of(LmPolicyStatus.ISSUED, LmPolicyStatus.CANCELLED);

    private final ContractRepository contracts;
    private final LmPolicyRepository policies;
    private final CancellationUnknownReasons unknownReasons;

    public TaskCancellationService(ContractRepository contracts, LmPolicyRepository policies,
            CancellationUnknownReasons unknownReasons) {
        this.contracts = contracts;
        this.policies = policies;
        this.unknownReasons = unknownReasons;
    }

    public void apply(ContractTaskCancellation cancellation, Instant now) {
        Cancellation outcome = CancelReasons.of(cancellation.cancelReason())
                .map(reason -> new Cancellation(reason, cancellation.cancelComment(), cancellation.cancelledAt()))
                .orElseGet(() -> unknownReasons.asOther(cancellation));
        UUID individualRequestId = cancellation.individualRequestId();
        List<Contract> runningContracts =
                contracts.findByRequestIndividualRequestIdAndStatusNotIn(individualRequestId, UNTOUCHED_CONTRACTS);
        contracts.lockFresh(runningContracts);
        cancelUnlessUntouched(runningContracts, UNTOUCHED_CONTRACTS, outcome, now);
        List<LmPolicy> runningPolicies =
                policies.findByRequestIndividualRequestIdAndStatusNotIn(individualRequestId, UNTOUCHED_POLICIES);
        policies.lockFresh(runningPolicies);
        cancelUnlessUntouched(runningPolicies, UNTOUCHED_POLICIES, outcome, now);
    }

    private static void cancelUnlessUntouched(List<? extends IssuanceRecord> locked, Set<?> untouched,
            Cancellation outcome, Instant now) {
        for (IssuanceRecord record : locked) {
            if (!untouched.contains(record.status())) {
                record.cancel(outcome, now);
            }
        }
    }
}
