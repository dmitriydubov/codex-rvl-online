package ru.projectx.datareference.service.policy;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.dto.PolicyResponse;
import ru.projectx.datareference.entity.catalog.InsuranceContract;
import ru.projectx.datareference.entity.policy.Policy;
import ru.projectx.datareference.repository.catalog.InsuranceContractRepository;
import ru.projectx.datareference.service.rest.RestReadTransaction;

@Service
public class PolicyQueryService implements PolicyReads {

    private final PolicyLookup lookup;
    private final InsuranceContractRepository insuranceContracts;
    private final RvlProgramCatalog rvlPrograms;

    public PolicyQueryService(
            PolicyLookup lookup, InsuranceContractRepository insuranceContracts, RvlProgramCatalog rvlPrograms) {

        this.lookup = lookup;
        this.insuranceContracts = insuranceContracts;
        this.rvlPrograms = rvlPrograms;
    }

    @Override
    @RestReadTransaction
    public PolicyResponse byId(UUID id) {
        return assemble(lookup.byId(id));
    }

    @Override
    @RestReadTransaction
    public PolicyResponse byExternalId(UUID externalId) {
        return assemble(lookup.byExternalId(externalId));
    }

    @Override
    @RestReadTransaction
    public PolicyResponse byNumber(String number, UUID contractExternalId) {
        return assemble(lookup.byNumber(number, contractExternalId));
    }

    private PolicyResponse assemble(Policy policy) {
        InsuranceContract contract = insuranceContracts.findByExternalId(policy.details().externalContractId())
                .orElse(null);
        return PolicyResponse.of(policy, contract, rvlPrograms.referencedBy(List.of(policy)));
    }
}
