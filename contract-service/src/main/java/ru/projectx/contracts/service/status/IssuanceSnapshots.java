package ru.projectx.contracts.service.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.IssuanceRequest;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.repository.IssuanceRequestRepository;
import ru.projectx.contracts.repository.LmPolicyRepository;

@Component
public class IssuanceSnapshots {

    private final IssuanceRequestRepository requests;
    private final ContractRepository contracts;
    private final LmPolicyRepository policies;

    public IssuanceSnapshots(IssuanceRequestRepository requests, ContractRepository contracts,
            LmPolicyRepository policies) {
        this.requests = requests;
        this.contracts = contracts;
        this.policies = policies;
    }

    public IssuanceSnapshot current(UUID individualRequestId) {
        Optional<IssuanceRequest> request = requests.findById(individualRequestId);
        return new IssuanceSnapshot(individualRequestId,
                request.map(IssuanceRequest::cancelled).orElse(false),
                request.map(IssuanceRequest::participants).orElse(List.of()),
                contracts.findByRequestIndividualRequestId(individualRequestId),
                policies.findByRequestIndividualRequestId(individualRequestId));
    }
}
