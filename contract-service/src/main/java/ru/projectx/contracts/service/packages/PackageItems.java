package ru.projectx.contracts.service.packages;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.contracts.dto.DocumentState;
import ru.projectx.contracts.dto.PackageItem;
import ru.projectx.contracts.dto.PackageItem.PackageAcceptance;
import ru.projectx.contracts.dto.PackageItem.PackageDocument;
import ru.projectx.contracts.dto.PackageItem.PackagePayment;
import ru.projectx.contracts.dto.PackageItem.PaymentState;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractAcceptance;
import ru.projectx.contracts.entity.contract.ContractRegistration;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.issuance.IssuanceRecord;
import ru.projectx.contracts.entity.issuance.IssuedPolicy;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.service.status.IssuanceSnapshot;

final class PackageItems {

    private PackageItems() {
    }

    static PackageItem of(RequestParticipant participant, IssuanceSnapshot snapshot,
            Map<UUID, ContractAcceptance> acceptances) {
        return switch (participant.kind()) {
            case NONE -> new PackageItem(participant.participantId(), DocumentKind.NONE, null, null, null,
                    DocumentState.NOT_REQUIRED, null, null, null, null, List.of(), null, null);
            case KM_CONTRACT -> contract(participant, snapshot, acceptances);
            case LM_POLICY -> policy(participant, snapshot.policyOf(participant.participantId()), snapshot);
        };
    }

    private static PackageItem contract(RequestParticipant participant, IssuanceSnapshot snapshot,
            Map<UUID, ContractAcceptance> acceptances) {
        Contract contract = snapshot.contractOf(participant.participantId());
        DocumentState state = PackageStates.of(contract.status());
        Optional<IssueError> error = shownError(contract, state);
        List<PackageDocument> documents = PackageDocuments.of(snapshot.formsOf(contract));
        return new PackageItem(participant.participantId(), DocumentKind.KM_CONTRACT, contract.id(), contract.number(),
                contract.state().entityVersion(), state, contract.premiumAmount(),
                contract.progress().registration().map(ContractRegistration::issuedPolicyNumber).orElse(null),
                Optional.ofNullable(acceptances.get(contract.id())).map(PackageItems::acceptance).orElse(null),
                payment(state, contract.premiumAmount()), documents, error.map(IssueError::code).orElse(null),
                error.map(IssueError::message).orElse(null));
    }

    private static PackageItem policy(RequestParticipant participant, LmPolicy policy, IssuanceSnapshot snapshot) {
        DocumentState state = PackageStates.of(policy.status());
        Optional<IssueError> error = shownError(policy, state);
        List<PackageDocument> documents = PackageDocuments.of(snapshot.formsOf(policy));
        return new PackageItem(participant.participantId(), DocumentKind.LM_POLICY, null, null, null, state, null,
                policy.issued().map(IssuedPolicy::issuedPolicyNumber).orElse(null), null, null, documents,
                error.map(IssueError::code).orElse(null), error.map(IssueError::message).orElse(null));
    }

    private static PackageAcceptance acceptance(ContractAcceptance acceptance) {
        return new PackageAcceptance(acceptance.offer().acceptedAt(), acceptance.offer().contractVersion());
    }

    private static @Nullable PackagePayment payment(DocumentState state, BigDecimal premium) {
        return state == DocumentState.ACCEPTED ? new PackagePayment(PaymentState.PENDING, premium) : null;
    }

    private static Optional<IssueError> shownError(IssuanceRecord record, DocumentState state) {
        return state == DocumentState.ERROR ? record.state().error() : Optional.empty();
    }
}
