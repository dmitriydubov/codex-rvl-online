package ru.projectx.contracts.service.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.IssuanceRecord;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.RequestParticipant;
import ru.projectx.contracts.message.IssuedDocumentKind;
import ru.projectx.contracts.message.KmContractState;
import ru.projectx.contracts.message.ParticipantStatus;
import ru.projectx.contracts.message.PaymentStatus;

final class ParticipantStatuses {

    private ParticipantStatuses() {
    }

    static ParticipantStatus of(RequestParticipant participant, IssuanceSnapshot snapshot) {
        UUID id = participant.participantId();
        return switch (participant.kind()) {
            case NONE -> ParticipantStatus.nothingIssued(id);
            case KM_CONTRACT -> kmContract(id, snapshot.contractOf(id));
            case LM_POLICY -> lmPolicy(id, snapshot.policyOf(id));
        };
    }

    private static ParticipantStatus kmContract(UUID id, Contract contract) {
        Optional<IssueError> error = currentError(contract);
        return new ParticipantStatus(id, IssuedDocumentKind.KM_CONTRACT, contract.id(),
                KmContractStates.of(contract.status()), contract.number(), PaymentStatus.NOT_REQUIRED, List.of(),
                error.map(IssueError::code).orElse(null), error.map(IssueError::message).orElse(null));
    }

    private static ParticipantStatus lmPolicy(UUID id, LmPolicy policy) {
        Optional<IssueError> error = currentError(policy);
        return new ParticipantStatus(id, IssuedDocumentKind.LM_POLICY, null, KmContractState.NOT_REQUIRED, null,
                PaymentStatus.NOT_REQUIRED, List.of(),
                error.map(IssueError::code).orElse(null), error.map(IssueError::message).orElse(null));
    }

    private static Optional<IssueError> currentError(IssuanceRecord record) {
        return record.state().error().filter(error -> record.status().error());
    }
}
