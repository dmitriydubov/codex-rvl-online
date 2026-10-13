package ru.projectx.contracts.service.status;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.entity.DocumentKind;
import ru.projectx.contracts.entity.IssuanceRecord;
import ru.projectx.contracts.entity.IssuanceStatus;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.entity.RequestParticipant;
import ru.projectx.contracts.message.RequestStatus;

public final class IssuanceSnapshot {

    private final UUID individualRequestId;
    private final boolean cancelled;
    private final List<RequestParticipant> composition;
    private final Map<UUID, Contract> latestContracts;
    private final Map<UUID, LmPolicy> latestPolicies;
    private final Map<UUID, ContractStatus> latestContractStatuses;
    private final Map<UUID, LmPolicyStatus> latestPolicyStatuses;
    private final Map<UUID, IssuanceStatus> recordStatuses;

    public IssuanceSnapshot(UUID individualRequestId, boolean cancelled, List<RequestParticipant> composition,
            List<Contract> contracts, List<LmPolicy> policies) {
        this.individualRequestId = individualRequestId;
        this.cancelled = cancelled;
        this.composition = List.copyOf(composition);
        this.latestContracts = latestBy(contracts, Contract::insuredParticipantId);
        this.latestPolicies = latestBy(policies, LmPolicy::participantId);
        this.latestContractStatuses = statuses(latestContracts, Contract::status);
        this.latestPolicyStatuses = statuses(latestPolicies, LmPolicy::status);
        this.recordStatuses = Stream.concat(contracts.stream(), policies.stream())
                .collect(Collectors.toUnmodifiableMap(IssuanceRecord::id, IssuanceRecord::status));
    }

    private static <R extends IssuanceRecord> Map<UUID, R> latestBy(List<R> records, Function<R, UUID> participant) {
        return records.stream().collect(Collectors.toUnmodifiableMap(participant, Function.identity(),
                BinaryOperator.maxBy(Comparator.comparing(IssuanceRecord::id))));
    }

    private static <R, S> Map<UUID, S> statuses(Map<UUID, R> latest, Function<R, S> status) {
        return latest.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> status.apply(entry.getValue())));
    }

    public UUID individualRequestId() {
        return individualRequestId;
    }

    public boolean cancelled() {
        return cancelled;
    }

    public List<RequestParticipant> composition() {
        return composition;
    }

    public Map<UUID, IssuanceStatus> recordStatuses() {
        return recordStatuses;
    }

    public Contract contractOf(UUID participantId) {
        return Optional.ofNullable(latestContracts.get(participantId))
                .orElseThrow(() -> missing("договора КМ", participantId));
    }

    public LmPolicy policyOf(UUID participantId) {
        return Optional.ofNullable(latestPolicies.get(participantId))
                .orElseThrow(() -> missing("полиса ЛМ", participantId));
    }

    public Optional<IssuanceStatus> statusOf(RequestParticipant participant) {
        UUID participantId = participant.participantId();
        return switch (participant.kind()) {
            case NONE -> Optional.empty();
            case KM_CONTRACT -> Optional.of(contractStatusOf(participantId));
            case LM_POLICY -> Optional.of(Optional.ofNullable(latestPolicyStatuses.get(participantId))
                    .orElseThrow(() -> missing("полиса ЛМ", participantId)));
        };
    }

    private ContractStatus contractStatusOf(UUID participantId) {
        return Optional.ofNullable(latestContractStatuses.get(participantId))
                .orElseThrow(() -> missing("договора КМ", participantId));
    }

    private static IllegalStateException missing(String record, UUID participantId) {
        return new IllegalStateException("у участника " + participantId + " нет " + record);
    }

    public RequestStatus requestStatus() {
        return RequestStatusRules.of(this);
    }

    public boolean hasUndraftedKmContract() {
        return composition.stream()
                .filter(participant -> participant.kind() == DocumentKind.KM_CONTRACT)
                .map(participant -> contractStatusOf(participant.participantId()))
                .anyMatch(KmContractStates::undrafted);
    }
}
