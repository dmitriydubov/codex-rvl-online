package ru.projectx.datareference.service.policy;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.dto.RelativePolicy;
import ru.projectx.datareference.entity.catalog.InsuranceContract;
import ru.projectx.datareference.entity.catalog.Program;
import ru.projectx.datareference.entity.policy.Policy;
import ru.projectx.datareference.repository.catalog.InsuranceContractRepository;
import ru.projectx.datareference.repository.policy.PolicyRepository;

@Component
public class RelativePolicies {

    private final PolicyRepository policies;
    private final InsuranceContractRepository insuranceContracts;
    private final RvlProgramCatalog rvlPrograms;

    public RelativePolicies(
            PolicyRepository policies, InsuranceContractRepository insuranceContracts, RvlProgramCatalog rvlPrograms) {

        this.policies = policies;
        this.insuranceContracts = insuranceContracts;
        this.rvlPrograms = rvlPrograms;
    }

    public Map<UUID, RelativePolicy> byExternalIds(Collection<UUID> externalIds) {
        if (externalIds.isEmpty()) {
            return Map.of();
        }
        List<Policy> found = policies.findAllWithProgramsByExternalIdIn(externalIds);
        policies.findAllWithServiceLinesByExternalIdIn(externalIds);
        Map<UUID, InsuranceContract> contracts = contractsOf(found);
        Map<UUID, Program> programs = rvlPrograms.referencedBy(found);
        return found.stream().collect(Collectors.toMap(Policy::externalId,
                policy -> RelativePolicy.of(policy, contracts.get(policy.details().externalContractId()), programs)));
    }

    private Map<UUID, InsuranceContract> contractsOf(List<Policy> found) {
        List<UUID> contractIds = found.stream().map(policy -> policy.details().externalContractId()).distinct().toList();
        return insuranceContracts.findAllByExternalIdIn(contractIds).stream()
                .collect(Collectors.toMap(InsuranceContract::externalId, Function.identity()));
    }
}
