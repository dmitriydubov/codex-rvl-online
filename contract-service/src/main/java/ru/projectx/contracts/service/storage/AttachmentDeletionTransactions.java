package ru.projectx.contracts.service.storage;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.document.DocumentAttachment;
import ru.projectx.contracts.entity.document.DocumentAttachments;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.AttachmentBusyException;
import ru.projectx.contracts.exception.AttachmentLockedException;
import ru.projectx.contracts.message.usk.KmContractEvents;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;
import ru.projectx.contracts.repository.journal.OutboxMessageRepository;

@Component
public class AttachmentDeletionTransactions {

    private final PrintedFormRepository documents;
    private final ContractRepository contracts;
    private final OutboxMessageRepository outbox;
    private final Clock clock;

    public AttachmentDeletionTransactions(PrintedFormRepository documents, ContractRepository contracts,
            OutboxMessageRepository outbox, Clock clock) {
        this.documents = documents;
        this.contracts = contracts;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public Optional<DeletionCandidate> prepare(UUID attachmentId) {
        return documents.findByAttachmentAttachmentId(attachmentId)
                .filter(existing -> existing.attachment().filter(DocumentAttachments::hasActiveAttachmentId).isPresent())
                .map(document -> candidate(attachmentId, document));
    }

    @Transactional
    public void markDeleted(UUID attachmentId, DeletionCandidate candidate, String requestId) {
        CertificateOwner owner = candidate.certificateOwner();
        if (owner != null) {
            lockAndEnsureUnlocked(attachmentId, owner);
        }
        documents.markDeletedByAttachmentId(attachmentId, clock.instant(), requestId);
    }

    private DeletionCandidate candidate(UUID attachmentId, PrintedForm document) {
        StoredFile file = document.storedFile().orElseThrow(
                () -> new IllegalStateException("у вложения " + document.id() + " нет файла"));
        if (document.docType() != PrintedFormType.MEDICAL_CERTIFICATE) {
            return new DeletionCandidate(file.key(), null);
        }
        DocumentAttachment attachment = document.attachment().orElseThrow();
        CertificateOwner owner = new CertificateOwner(
                Objects.requireNonNull(attachment.individualRequestId()),
                Objects.requireNonNull(attachment.participantId()));
        lockAndEnsureUnlocked(attachmentId, owner);
        return new DeletionCandidate(file.key(), owner);
    }

    private void lockAndEnsureUnlocked(UUID attachmentId, CertificateOwner owner) {
        List<Contract> pairContracts = contracts.findByRequestIndividualRequestIdAndInsuredParticipantId(
                owner.individualRequestId(), owner.participantId());
        if (pairContracts.isEmpty()) {
            return;
        }
        lockContracts(attachmentId, pairContracts);
        if (hasSubmittedContract(owner.individualRequestId(), pairContracts)) {
            throw new AttachmentLockedException(attachmentId);
        }
    }

    private void lockContracts(UUID attachmentId, List<Contract> pairContracts) {
        try {
            contracts.lockFresh(pairContracts);
        } catch (PessimisticLockingFailureException busy) {
            throw new AttachmentBusyException(attachmentId, busy);
        }
    }

    private boolean hasSubmittedContract(UUID individualRequestId, List<Contract> pairContracts) {
        if (pairContracts.stream().anyMatch(AttachmentDeletionTransactions::submittedOrIssued)) {
            return true;
        }
        List<UUID> contractIds = pairContracts.stream().map(Contract::id).toList();
        return outbox.existsByCorrelationIdAndAggregateTypeAndAggregateIdInAndEventType(
                individualRequestId, KmContractEvents.CONTRACT_AGGREGATE, contractIds,
                KmContractEvents.KM_CONTRACT_SUBMITTED);
    }

    private static boolean submittedOrIssued(Contract contract) {
        ContractStatus status = contract.status();
        return status == ContractStatus.SUBMITTED || status == ContractStatus.ISSUED;
    }
}
