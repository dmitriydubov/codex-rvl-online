package ru.projectx.contracts.service.storage;

import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.dto.AttachmentResponse;
import ru.projectx.contracts.entity.issuance.IssuanceRequest;
import ru.projectx.contracts.entity.document.ParticipantAttachment;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.AttachmentConflictException;
import ru.projectx.contracts.exception.ParticipantNotFoundException;
import ru.projectx.contracts.exception.RequestCancelledException;
import ru.projectx.contracts.exception.RequestNotFoundException;
import ru.projectx.contracts.repository.IssuanceRequestRepository;
import ru.projectx.contracts.repository.PrintedFormRepository;

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
    public Optional<AttachmentResponse> accepted(CertificateUpload upload, String fileHash) {
        ensureOpenFor(upload);
        return documents.findByAttachmentAttachmentId(upload.attachmentId())
                .map(certificate -> sameUpload(certificate, upload, fileHash));
    }

    @Transactional
    public AttachmentResponse register(CertificateUpload upload, StoredFile file) {
        ensureOpenFor(upload);
        PrintedForm certificate = PrintedForm.receivedCertificate(owner(upload), file, clock.instant());
        return AttachmentResponses.of(documents.saveAndFlush(certificate));
    }

    private void ensureOpenFor(CertificateUpload upload) {
        IssuanceRequest request = requests.findById(upload.individualRequestId())
                .orElseThrow(() -> new RequestNotFoundException(upload.individualRequestId()));
        if (request.participants().stream().map(RequestParticipant::participantId)
                .noneMatch(upload.participantId()::equals)) {
            throw new ParticipantNotFoundException(upload.individualRequestId(), upload.participantId());
        }
        if (request.cancelled()) {
            throw new RequestCancelledException(upload.individualRequestId());
        }
    }

    private static AttachmentResponse sameUpload(PrintedForm certificate, CertificateUpload upload, String fileHash) {
        boolean sameOwner = certificate.attachment().filter(owner(upload)::equals).isPresent();
        boolean sameContent = certificate.storedFile().map(StoredFile::hash).filter(fileHash::equals).isPresent();
        if (!sameOwner || !sameContent) {
            throw new AttachmentConflictException(upload.attachmentId());
        }
        return AttachmentResponses.of(certificate);
    }

    private static ParticipantAttachment owner(CertificateUpload upload) {
        return new ParticipantAttachment(upload.individualRequestId(), upload.participantId(), upload.attachmentId());
    }
}
