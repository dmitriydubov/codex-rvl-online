package ru.projectx.contracts.service.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractProgress;
import ru.projectx.contracts.entity.contract.ContractRegistration;
import ru.projectx.contracts.entity.issuance.IssuanceRecord;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.issuance.IssuedPolicy;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.message.IssuedDocumentKind;
import ru.projectx.contracts.message.KmContractState;
import ru.projectx.contracts.message.ParticipantStatus;
import ru.projectx.contracts.message.PaymentStatus;
import ru.projectx.contracts.message.StatusDocument;

final class ParticipantStatuses {

    private ParticipantStatuses() {
    }

    static ParticipantStatus of(RequestParticipant participant, IssuanceSnapshot snapshot) {
        UUID id = participant.participantId();
        return switch (participant.kind()) {
            case NONE -> ParticipantStatus.nothingIssued(id);
            case KM_CONTRACT -> kmContract(id, snapshot);
            case LM_POLICY -> lmPolicy(id, snapshot);
        };
    }

    private static ParticipantStatus lmPolicy(UUID id, IssuanceSnapshot snapshot) {
        LmPolicy policy = snapshot.policyOf(id);
        return lmPolicy(id, policy, snapshot.formsOf(policy));
    }

    private static ParticipantStatus kmContract(UUID id, IssuanceSnapshot snapshot) {
        Contract contract = snapshot.contractOf(id);
        return kmContract(id, contract, snapshot.formsOf(contract));
    }

    private static ParticipantStatus kmContract(UUID id, Contract contract, List<PrintedForm> forms) {
        Optional<IssueError> error = currentError(contract);
        ContractProgress progress = contract.progress();
        Optional<ContractRegistration> registration = progress.registration();
        return new ParticipantStatus(id, IssuedDocumentKind.KM_CONTRACT, contract.id(),
                KmContractStates.of(contract.status()), contract.number(),
                registration.map(ContractRegistration::issuedPolicyId).orElse(null),
                registration.map(ContractRegistration::issuedPolicyNumber).orElse(null), null, null,
                payment(progress), documents(forms), error.map(IssueError::code).orElse(null),
                error.map(IssueError::message).orElse(null));
    }

    private static ParticipantStatus lmPolicy(UUID id, LmPolicy policy, List<PrintedForm> forms) {
        Optional<IssueError> error = currentError(policy);
        Optional<IssuedPolicy> issued = policy.issued();
        return new ParticipantStatus(id, IssuedDocumentKind.LM_POLICY, null, KmContractState.NOT_REQUIRED, null, null,
                null, issued.map(IssuedPolicy::issuedPolicyId).orElse(null),
                issued.map(IssuedPolicy::issuedPolicyNumber).orElse(null), PaymentStatus.NOT_REQUIRED,
                documents(forms), error.map(IssueError::code).orElse(null), error.map(IssueError::message).orElse(null));
    }

    private static PaymentStatus payment(ContractProgress progress) {
        return progress.paidAt().isPresent() ? PaymentStatus.PAID : PaymentStatus.NOT_REQUIRED;
    }

    private static List<StatusDocument> documents(List<PrintedForm> forms) {
        return forms.stream().map(ParticipantStatuses::document).toList();
    }

    private static StatusDocument document(PrintedForm form) {
        return new StatusDocument(form.id(), form.docType().name(), form.kind().name(), form.uskFileUrl().orElse(null));
    }

    private static Optional<IssueError> currentError(IssuanceRecord record) {
        return record.state().error().filter(error -> record.status().error());
    }
}
