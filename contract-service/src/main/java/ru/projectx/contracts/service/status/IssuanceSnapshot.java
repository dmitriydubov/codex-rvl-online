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
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.issuance.IssuanceRecord;
import ru.projectx.contracts.entity.issuance.IssuanceStatus;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.message.RequestStatus;

public final class IssuanceSnapshot {

    private static final String KM_CONTRACT = "договора КМ";
    private static final String LM_POLICY = "полиса ЛМ";

    private final UUID individualRequestId;
    private final boolean cancelled;
    private final List<RequestParticipant> composition;
    private final List<RecordOutcome> records;
    private final Map<UUID, RecordOutcome> byRecord;
    private final Map<UUID, RecordOutcome> latestByParticipant;
    private final Map<UUID, Contract> latestContracts;
    private final Map<UUID, LmPolicy> latestPolicies;
    private final Map<UUID, List<PrintedForm>> formsByOwner;

    public IssuanceSnapshot(UUID individualRequestId, boolean cancelled, List<RequestParticipant> composition,
            List<Contract> contracts, List<LmPolicy> policies, List<PrintedForm> forms) {
        this.individualRequestId = individualRequestId;
        this.cancelled = cancelled;
        this.composition = List.copyOf(composition);
        this.records = Stream.concat(contracts.stream(), policies.stream()).map(RecordOutcome::of).toList();
        this.byRecord = records.stream()
                .collect(Collectors.toUnmodifiableMap(RecordOutcome::recordId, Function.identity()));
        this.latestByParticipant = latestBy(records, RecordOutcome::participantId, RecordOutcome::recordId);
        this.latestContracts = latestBy(contracts, IssuanceRecord::participantId, IssuanceRecord::id);
        this.latestPolicies = latestBy(policies, IssuanceRecord::participantId, IssuanceRecord::id);
        this.formsByOwner = forms.stream()
                .filter(form -> form.contractId().or(form::lmPolicyId).isPresent())
                .collect(Collectors.groupingBy(form -> form.contractId().or(form::lmPolicyId).orElseThrow()));
    }

    private static <T> Map<UUID, T> latestBy(List<T> items, Function<T, UUID> participant, Function<T, UUID> order) {
        return items.stream().collect(Collectors.toUnmodifiableMap(participant, Function.identity(),
                BinaryOperator.maxBy(Comparator.comparing(order))));
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

    List<RecordOutcome> records() {
        return records;
    }

    boolean knows(RecordOutcome record) {
        return record.looksLike(byRecord.getOrDefault(record.recordId(),
                latestByParticipant.get(record.participantId())));
    }

    public Contract contractOf(UUID participantId) {
        return latest(latestContracts, participantId, KM_CONTRACT);
    }

    public LmPolicy policyOf(UUID participantId) {
        return latest(latestPolicies, participantId, LM_POLICY);
    }

    public List<PrintedForm> formsOf(IssuanceRecord record) {
        return formsByOwner.getOrDefault(record.id(), List.of());
    }

    public Optional<IssuanceStatus> statusOf(RequestParticipant participant) {
        UUID participantId = participant.participantId();
        return switch (participant.kind()) {
            case NONE -> Optional.empty();
            case KM_CONTRACT -> Optional.of(latest(latestByParticipant, participantId, KM_CONTRACT).status());
            case LM_POLICY -> Optional.of(latest(latestByParticipant, participantId, LM_POLICY).status());
        };
    }

    private static <V> V latest(Map<UUID, V> byParticipant, UUID participantId, String record) {
        return Optional.ofNullable(byParticipant.get(participantId))
                .orElseThrow(() -> new IllegalStateException("у участника " + participantId + " нет " + record));
    }

    public RequestStatus requestStatus() {
        return RequestStatusRules.of(this);
    }
}
