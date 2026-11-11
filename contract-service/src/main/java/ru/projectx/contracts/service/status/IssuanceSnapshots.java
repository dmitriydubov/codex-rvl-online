package ru.projectx.contracts.service.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.issuance.IssuanceRequest;
import ru.projectx.contracts.entity.payment.OrderInvoice;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.repository.issuance.IssuanceRequestRepository;
import ru.projectx.contracts.repository.issuance.LmPolicyRepository;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;
import ru.projectx.contracts.repository.payment.OrderInvoiceRepository;

@Component
public class IssuanceSnapshots {

    private final IssuanceRequestRepository requests;
    private final ContractRepository contracts;
    private final LmPolicyRepository policies;
    private final PrintedFormRepository forms;

    private final OrderInvoiceRepository invoices;

    public IssuanceSnapshots(IssuanceRequestRepository requests, ContractRepository contracts,
            LmPolicyRepository policies, PrintedFormRepository forms, OrderInvoiceRepository invoices) {
        this.requests = requests;
        this.contracts = contracts;
        this.policies = policies;
        this.forms = forms;
        this.invoices = invoices;
    }

    public IssuanceSnapshot current(UUID individualRequestId) {
        Optional<IssuanceRequest> request = requests.findById(individualRequestId);
        List<Contract> requestContracts = contracts.findByRequestIndividualRequestId(individualRequestId);
        List<LmPolicy> requestPolicies = policies.findByRequestIndividualRequestId(individualRequestId);
        List<UUID> contractIds = requestContracts.stream().map(Contract::id).toList();
        return new IssuanceSnapshot(individualRequestId,
                request.map(IssuanceRequest::cancelled).orElse(false),
                request.map(IssuanceRequest::participants).orElse(List.of()),
                requestContracts,
                requestPolicies,
                forms.findByLmPolicyIdInOrContractIdInOrderByCreatedAtAscIdAsc(
                        requestPolicies.stream().map(LmPolicy::id).toList(), contractIds),
                invoices.findByContractIdIn(contractIds).stream().map(OrderInvoice::view).toList());
    }
}
