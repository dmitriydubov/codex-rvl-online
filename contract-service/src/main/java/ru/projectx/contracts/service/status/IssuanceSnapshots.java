package ru.projectx.contracts.service.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.issuance.IssuanceRequest;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.repository.IssuanceRequestRepository;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.repository.PrintedFormRepository;

@Component
public class IssuanceSnapshots {

    private final IssuanceRequestRepository requests;
    private final ContractRepository contracts;
    private final LmPolicyRepository policies;
    private final PrintedFormRepository forms;

    public IssuanceSnapshots(IssuanceRequestRepository requests, ContractRepository contracts,
            LmPolicyRepository policies, PrintedFormRepository forms) {
        this.requests = requests;
        this.contracts = contracts;
        this.policies = policies;
        this.forms = forms;
    }

    public IssuanceSnapshot current(UUID individualRequestId) {
        Optional<IssuanceRequest> request = requests.findById(individualRequestId);
        List<Contract> requestContracts = contracts.findByRequestIndividualRequestId(individualRequestId);
        List<LmPolicy> requestPolicies = policies.findByRequestIndividualRequestId(individualRequestId);
        return new IssuanceSnapshot(individualRequestId,
                request.map(IssuanceRequest::cancelled).orElse(false),
                request.map(IssuanceRequest::participants).orElse(List.of()),
                requestContracts,
                requestPolicies,
                forms.findByLmPolicyIdInOrContractIdInOrderByCreatedAtAscIdAsc(
                        requestPolicies.stream().map(LmPolicy::id).toList(),
                        requestContracts.stream().map(Contract::id).toList()));
    }
}
