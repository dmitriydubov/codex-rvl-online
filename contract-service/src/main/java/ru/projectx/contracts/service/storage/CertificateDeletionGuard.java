package ru.projectx.contracts.service.storage;

import java.util.List;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.document.DocumentAttachment;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.exception.AttachmentLockedException;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.repository.journal.OutboxMessageRepository;

@Component
class CertificateDeletionGuard {

    private final ContractRepository contracts;
    private final OutboxMessageRepository outbox;

    CertificateDeletionGuard(ContractRepository contracts, OutboxMessageRepository outbox) {
        this.contracts = contracts;
        this.outbox = outbox;
    }

    List<Contract> lock(PrintedForm attachment) {
        if (attachment.docType() != PrintedFormType.MEDICAL_CERTIFICATE) {
            return List.of();
        }
        DocumentAttachment owner = attachment.attachment().orElseThrow();
        List<Contract> pair = contracts.findByRequestIndividualRequestIdAndInsuredParticipantId(
                owner.individualRequestId(), owner.participantId());
        contracts.lockFresh(pair);
        return pair;
    }

    void requireUnsubmitted(PrintedForm attachment, List<Contract> locked) {
        if (!locked.isEmpty() && hasSubmission(attachment, locked)) {
            throw new AttachmentLockedException();
        }
    }

    private boolean hasSubmission(PrintedForm attachment, List<Contract> locked) {
        return locked.stream().anyMatch(CertificateDeletionGuard::submitted)
                || outbox.existsKmContractSubmission(attachment.attachment().orElseThrow().individualRequestId(),
                        locked.stream().map(Contract::id).toList());
    }

    private static boolean submitted(Contract contract) {
        return contract.status() == ContractStatus.SUBMITTED || contract.status() == ContractStatus.ISSUED;
    }
}
