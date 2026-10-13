package ru.projectx.contracts.service.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.DocumentKind;
import ru.projectx.contracts.entity.IssuanceRecord;
import ru.projectx.contracts.entity.IssuanceStatus;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.RequestParticipant;
import ru.projectx.contracts.message.RequestStatus;

public final class IssuanceSnapshot {

    private final UUID individualRequestId;
    private final boolean cancelled;
    private final List<RequestParticipant> composition;
    private final Map<UUID, Contract> latestContracts;
    private final Map<UUID, LmPolicy> latestPolicies;
    private final Map<UUID, IssuanceStatus> recordStatuses;

    public IssuanceSnapshot(UUID individualRequestId, boolean cancelled, List<RequestParticipant> composition,
            List<Contract> contracts, List<LmPolicy> policies) {
        this.individualRequestId = individualRequestId;
        this.cancelled = cancelled;
        this.composition = List.copyOf(composition);
        this.latestContracts = latestBy(contracts, Contract::insuredParticipantId);
        this.latestPolicies = latestBy(policies, LmPolicy::participantId);
        this.recordStatuses = Stream.concat(contracts.stream(), policies.stream())
                .collect(Collectors.toUnmodifiableMap(IssuanceRecord::id, IssuanceRecord::status));
    }

    private static <R extends IssuanceRecord> Map<UUID, R> latestBy(List<R> records, Function<R, UUID> participant) {
        return records.stream().collect(Collectors.toUnmodifiableMap(participant, Function.identity(),
                (first, second) -> first.id().compareTo(second.id()) > 0 ? first : second));
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
                .orElseThrow(() -> new IllegalStateException("у участника " + participantId + " нет договора КМ"));
    }

    public LmPolicy policyOf(UUID participantId) {
        return Optional.ofNullable(latestPolicies.get(participantId))
                .orElseThrow(() -> new IllegalStateException("у участника " + participantId + " нет полиса ЛМ"));
    }

    public Optional<IssuanceStatus> statusOf(RequestParticipant participant) {
        return switch (participant.kind()) {
            case NONE -> Optional.empty();
            case KM_CONTRACT -> Optional.of(contractOf(participant.participantId()).status());
            case LM_POLICY -> Optional.of(policyOf(participant.participantId()).status());
        };
    }

    public RequestStatus requestStatus() {
        return RequestStatusRules.of(this);
    }

    public boolean hasUndraftedKmContract() {
        return composition.stream()
                .filter(participant -> participant.kind() == DocumentKind.KM_CONTRACT)
                .map(participant -> contractOf(participant.participantId()).status())
                .anyMatch(KmContractStates::undrafted);
    }
}
