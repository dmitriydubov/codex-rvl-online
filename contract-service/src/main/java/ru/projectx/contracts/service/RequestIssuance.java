package ru.projectx.contracts.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.DocumentKind;
import ru.projectx.contracts.entity.LmPolicy;

public final class RequestIssuance {

    private final Map<UUID, ActiveIssuance> byParticipant = new LinkedHashMap<>();

    public RequestIssuance(List<Contract> contracts, List<LmPolicy> policies) {
        contracts.forEach(contract -> byParticipant.put(contract.participantId(),
                new ActiveIssuance(DocumentKind.KM_CONTRACT, KmContractIssuance.KIND, contract)));
        policies.forEach(policy -> byParticipant.put(policy.participantId(),
                new ActiveIssuance(DocumentKind.LM_POLICY, LmPolicyIssuance.KIND, policy)));
    }

    public Optional<ActiveIssuance> of(UUID participantId) {
        return Optional.ofNullable(byParticipant.get(participantId));
    }

    public Optional<Contract> contractOf(UUID participantId) {
        return of(participantId).filter(active -> active.kind() == DocumentKind.KM_CONTRACT)
                .map(active -> (Contract) active.record());
    }

    public Optional<LmPolicy> policyOf(UUID participantId) {
        return of(participantId).filter(active -> active.kind() == DocumentKind.LM_POLICY)
                .map(active -> (LmPolicy) active.record());
    }

    public void forget(UUID participantId) {
        byParticipant.remove(participantId);
    }

    public List<ActiveIssuance> leftovers() {
        return List.copyOf(byParticipant.values());
    }
}
