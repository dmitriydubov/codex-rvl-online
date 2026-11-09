package ru.projectx.contracts.service.cancellation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.issuance.IssuanceRecord;
import ru.projectx.contracts.entity.issuance.IssuanceStatus;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.policy.LmPolicyStatus;
import ru.projectx.contracts.message.task.ContractTaskCancellation;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.repository.issuance.LmPolicyRepository;
import ru.projectx.contracts.service.payment.cancellation.PaymentOrderWithdrawal;

@Service
public class TaskCancellationService {

    private static final Set<ContractStatus> UNTOUCHED_CONTRACTS = Set.of(ContractStatus.ISSUED, ContractStatus.CANCELLED);
    private static final Set<LmPolicyStatus> UNTOUCHED_POLICIES = Set.of(LmPolicyStatus.ISSUED, LmPolicyStatus.CANCELLED);

    private final ContractRepository contracts;
    private final LmPolicyRepository policies;
    private final CancellationUnknownReasons unknownReasons;
    private final PaymentOrderWithdrawal paymentOrders;

    public TaskCancellationService(ContractRepository contracts, LmPolicyRepository policies,
            CancellationUnknownReasons unknownReasons, PaymentOrderWithdrawal paymentOrders) {
        this.contracts = contracts;
        this.policies = policies;
        this.unknownReasons = unknownReasons;
        this.paymentOrders = paymentOrders;
    }

    public void apply(ContractTaskCancellation cancellation, Instant now) {
        Cancellation outcome = CancelReasons.of(cancellation.cancelReason())
                .map(reason -> new Cancellation(reason, cancellation.cancelComment(), cancellation.cancelledAt()))
                .orElseGet(() -> unknownReasons.asOther(cancellation));
        UUID individualRequestId = cancellation.individualRequestId();
        List<Contract> runningContracts =
                contracts.findByRequestIndividualRequestIdAndStatusNotIn(individualRequestId, UNTOUCHED_CONTRACTS);
        contracts.lockFresh(runningContracts);
        for (IssuanceRecord cancelled : cancelUnlessUntouched(runningContracts, UNTOUCHED_CONTRACTS, outcome, now)) {
            paymentOrders.contractStopped(cancelled.id(), now);
        }
        List<LmPolicy> runningPolicies =
                policies.findByRequestIndividualRequestIdAndStatusNotIn(individualRequestId, UNTOUCHED_POLICIES);
        policies.lockFresh(runningPolicies);
        cancelUnlessUntouched(runningPolicies, UNTOUCHED_POLICIES, outcome, now);
    }

    private static List<IssuanceRecord> cancelUnlessUntouched(List<? extends IssuanceRecord> locked,
            Set<? extends IssuanceStatus> untouched, Cancellation outcome, Instant now) {
        List<IssuanceRecord> cancelled = new ArrayList<>();
        for (IssuanceRecord record : locked) {
            if (!untouched.contains(record.status())) {
                record.cancel(outcome, now);
                cancelled.add(record);
            }
        }
        return cancelled;
    }
}
