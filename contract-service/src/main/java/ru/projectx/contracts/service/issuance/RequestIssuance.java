package ru.projectx.contracts.service.issuance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.policy.LmPolicy;

public final class RequestIssuance {

    private final Map<UUID, ActiveIssuance> byParticipant = new LinkedHashMap<>();

    private final Map<UUID, Contract> awaitingOverdue = new LinkedHashMap<>();

    public RequestIssuance(List<Contract> contracts, List<LmPolicy> policies) {
        this(contracts, policies, List.of());
    }

    public RequestIssuance(List<Contract> contracts, List<LmPolicy> policies, List<Contract> awaitingOverdue) {
        awaitingOverdue.forEach(contract -> this.awaitingOverdue.put(contract.participantId(), contract));
        contracts.forEach(contract -> byParticipant.merge(contract.participantId(), kmContract(contract),
                RequestIssuance::issuedFirst));
        policies.forEach(policy -> byParticipant.merge(policy.participantId(),
                new ActiveIssuance(DocumentKind.LM_POLICY, LmPolicyIssuance.KIND, policy), RequestIssuance::issuedFirst));
    }

    private static ActiveIssuance issuedFirst(ActiveIssuance known, ActiveIssuance another) {
        return another.issued() && !known.issued() ? another : known;
    }

    public Optional<ActiveIssuance> of(UUID participantId) {
        return Optional.ofNullable(byParticipant.get(participantId))
                .or(() -> awaitingOverdueOf(participantId).map(RequestIssuance::kmContract));
    }

    public Optional<Contract> contractOf(UUID participantId) {
        return of(participantId).filter(active -> active.kind() == DocumentKind.KM_CONTRACT)
                .map(active -> (Contract) active.record())
                .or(() -> awaitingOverdueOf(participantId));
    }

    private Optional<Contract> awaitingOverdueOf(UUID participantId) {
        return Optional.ofNullable(awaitingOverdue.get(participantId));
    }

    private static ActiveIssuance kmContract(Contract contract) {
        return new ActiveIssuance(DocumentKind.KM_CONTRACT, KmContractIssuance.KIND, contract);
    }

    public Optional<LmPolicy> policyOf(UUID participantId) {
        return of(participantId).filter(active -> active.kind() == DocumentKind.LM_POLICY)
                .map(active -> (LmPolicy) active.record());
    }

    public void forget(UUID participantId) {
        byParticipant.remove(participantId);
        awaitingOverdue.remove(participantId);
    }

    public List<ActiveIssuance> leftovers() {
        return Stream.concat(byParticipant.values().stream(),
                awaitingOverdue.values().stream().map(RequestIssuance::kmContract)).toList();
    }
}
