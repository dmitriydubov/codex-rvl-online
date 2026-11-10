package ru.projectx.contracts.service.cancellation;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.policy.LmPolicyStatus;
import ru.projectx.contracts.message.task.ContractTaskCancellation;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.repository.issuance.LmPolicyRepository;
import ru.projectx.contracts.service.payment.cancellation.ContractStops;

@Service
public class TaskCancellationService {

    private static final Set<ContractStatus> UNTOUCHED_CONTRACTS = Set.of(ContractStatus.ISSUED, ContractStatus.CANCELLED);
    private static final Set<LmPolicyStatus> UNTOUCHED_POLICIES = Set.of(LmPolicyStatus.ISSUED, LmPolicyStatus.CANCELLED);

    private final ContractRepository contracts;
    private final LmPolicyRepository policies;
    private final CancellationUnknownReasons unknownReasons;
    private final ContractStops contractStops;

    public TaskCancellationService(ContractRepository contracts, LmPolicyRepository policies,
            CancellationUnknownReasons unknownReasons, ContractStops contractStops) {
        this.contracts = contracts;
        this.policies = policies;
        this.unknownReasons = unknownReasons;
        this.contractStops = contractStops;
    }

    public void apply(ContractTaskCancellation cancellation, Instant now) {
        Cancellation outcome = CancelReasons.of(cancellation.cancelReason())
                .map(reason -> new Cancellation(reason, cancellation.cancelComment(), cancellation.cancelledAt()))
                .orElseGet(() -> unknownReasons.asOther(cancellation));
        UUID individualRequestId = cancellation.individualRequestId();
        List<Contract> runningContracts =
                contracts.findByRequestIndividualRequestIdAndStatusNotIn(individualRequestId, UNTOUCHED_CONTRACTS);
        contracts.lockFresh(runningContracts);
        runningContracts.stream()
                .filter(contract -> !UNTOUCHED_CONTRACTS.contains(contract.status()))
                .toList()
                .forEach(contract -> contractStops.stop(contract, outcome, now));
        List<LmPolicy> runningPolicies =
                policies.findByRequestIndividualRequestIdAndStatusNotIn(individualRequestId, UNTOUCHED_POLICIES);
        policies.lockFresh(runningPolicies);
        runningPolicies.stream()
                .filter(policy -> !UNTOUCHED_POLICIES.contains(policy.status()))
                .toList()
                .forEach(policy -> policy.cancel(outcome, now));
    }
}
