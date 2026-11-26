package ru.projectx.contracts.service.storage;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.dto.AttachmentReceipt;
import ru.projectx.contracts.entity.document.AttachmentOwner;
import ru.projectx.contracts.entity.document.AttachmentOwners;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.AttachmentConflictException;
import ru.projectx.contracts.exception.RequestCancelledException;
import ru.projectx.contracts.repository.issuance.IssuanceRequestRepository;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;

@Component
public class AttachmentRegistry {

    private final IssuanceRequestRepository requests;
    private final PrintedFormRepository documents;
    private final Clock clock;

    public AttachmentRegistry(IssuanceRequestRepository requests, PrintedFormRepository documents, Clock clock) {
        this.requests = requests;
        this.documents = documents;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<AttachmentReceipt> accepted(AttachmentUpload upload, String fileHash) {
        ensureOpenFor(upload);
        AttachmentOwner owner = upload.owner();
        return documents.findByAttachmentAttachmentId(owner.attachmentId())
                .map(document -> sameUpload(document, owner, fileHash));
    }

    @Transactional
    public AttachmentReceipt register(AttachmentUpload upload, StoredFile file) {
        ensureOpenFor(upload);
        PrintedForm document = PrintedForm.receivedAttachment(upload.owner(), file, clock.instant());
        return AttachmentResponses.of(documents.saveAndFlush(document));
    }

    public void ensureOpenFor(AttachmentUpload upload) {
        UUID request = upload.owner().individualRequestId();
        if (request != null && requests.existsByIndividualRequestIdAndCancelledAtIsNotNull(request)) {
            throw new RequestCancelledException(request);
        }
    }

    private static AttachmentReceipt sameUpload(PrintedForm document, AttachmentOwner owner, String fileHash) {
        boolean sameContent = document.storedFile().map(StoredFile::hash).filter(fileHash::equals).isPresent();
        if (!sameContent || !activeFor(document, owner)) {
            throw new AttachmentConflictException(owner.attachmentId());
        }
        return AttachmentResponses.of(document);
    }

    private static boolean activeFor(PrintedForm document, AttachmentOwner owner) {
        return !document.deleted() && document.docType() == AttachmentOwners.documentType(owner)
                && document.attachment().filter(owner::equals).isPresent();
    }
}
