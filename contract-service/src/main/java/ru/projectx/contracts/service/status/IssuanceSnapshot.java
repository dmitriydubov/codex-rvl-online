package ru.projectx.contracts.service.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.issuance.IssuanceRecord;
import ru.projectx.contracts.entity.issuance.IssuanceStatus;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.entity.payment.InvoiceView;
import ru.projectx.contracts.message.status.RequestStatus;

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
    private final Map<UUID, InvoiceView> invoicesByContract;

    public IssuanceSnapshot(UUID individualRequestId, boolean cancelled, List<RequestParticipant> composition,
            List<Contract> contracts, List<LmPolicy> policies, List<PrintedForm> forms) {
        this(individualRequestId, cancelled, composition, contracts, policies, forms, List.of());
    }

    public IssuanceSnapshot(UUID individualRequestId, boolean cancelled, List<RequestParticipant> composition,
            List<Contract> contracts, List<LmPolicy> policies, List<PrintedForm> forms, List<InvoiceView> invoices) {
        this.individualRequestId = individualRequestId;
        this.cancelled = cancelled;
        this.composition = List.copyOf(composition);
        this.invoicesByContract = invoices.stream()
                .collect(Collectors.toUnmodifiableMap(InvoiceView::contractId, Function.identity()));
        this.records = Stream.concat(
                contracts.stream().map(contract -> RecordOutcome.of(contract,
                        Optional.ofNullable(invoicesByContract.get(contract.id())))),
                policies.stream().map(RecordOutcome::of)).toList();
        this.byRecord = records.stream()
                .collect(Collectors.toUnmodifiableMap(RecordOutcome::recordId, Function.identity()));
        this.latestByParticipant =
                ParticipantRecords.latestBy(records, RecordOutcome::participantId, RecordOutcome::recordId);
        this.latestContracts = ParticipantRecords.latestBy(contracts, IssuanceRecord::participantId, IssuanceRecord::id);
        this.latestPolicies = ParticipantRecords.latestBy(policies, IssuanceRecord::participantId, IssuanceRecord::id);
        this.formsByOwner = forms.stream()
                .filter(form -> form.contractId().or(form::lmPolicyId).isPresent())
                .collect(Collectors.groupingBy(form -> form.contractId().or(form::lmPolicyId).orElseThrow()));
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

    boolean resumed(RecordOutcome record) {
        return record.resumedSince(byRecord.get(record.recordId()));
    }

    public PaymentOffer paymentOf(Contract contract) {
        return PaymentOffer.of(contract.status(), Optional.ofNullable(invoicesByContract.get(contract.id())));
    }

    public Contract contractOf(UUID participantId) {
        return ParticipantRecords.latest(latestContracts, participantId, KM_CONTRACT);
    }

    public LmPolicy policyOf(UUID participantId) {
        return ParticipantRecords.latest(latestPolicies, participantId, LM_POLICY);
    }

    public List<PrintedForm> formsOf(IssuanceRecord record) {
        return formsByOwner.getOrDefault(record.id(), List.of());
    }

    public Optional<IssuanceStatus> statusOf(RequestParticipant participant) {
        UUID participantId = participant.participantId();
        return switch (participant.kind()) {
            case NONE -> Optional.empty();
            case KM_CONTRACT ->
                    Optional.of(ParticipantRecords.latest(latestByParticipant, participantId, KM_CONTRACT).status());
            case LM_POLICY ->
                    Optional.of(ParticipantRecords.latest(latestByParticipant, participantId, LM_POLICY).status());
        };
    }

    public RequestStatus requestStatus() {
        return RequestStatusRules.of(this);
    }
}
