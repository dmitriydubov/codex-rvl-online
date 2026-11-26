package ru.projectx.contracts.service.storage;

import java.util.Objects;
import ru.projectx.contracts.dto.AttachmentDetails;
import ru.projectx.contracts.dto.AttachmentReceipt;
import ru.projectx.contracts.dto.AttachmentResponse;
import ru.projectx.contracts.dto.ConsentResponse;
import ru.projectx.contracts.entity.document.AttachmentOwner;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.StoredFile;

final class AttachmentResponses {

    private AttachmentResponses() {
    }

    static AttachmentReceipt of(PrintedForm document) {
        AttachmentOwner owner = document.attachment().orElseThrow(
                () -> new IllegalStateException("документ " + document.id() + " — не вложение"));
        StoredFile file = document.storedFile().orElseThrow(
                () -> new IllegalStateException("у вложения " + document.id() + " нет файла"));
        AttachmentDetails details = new AttachmentDetails(document.id(), owner.attachmentId(), file.contentType(),
                file.size(), file.hash(), document.createdAt());
        if (document.docType() == PrintedFormType.PD_CONSENT) {
            return new ConsentResponse(Objects.requireNonNull(owner.policyId()),
                    Objects.requireNonNull(owner.familyMemberId()), details);
        }
        return new AttachmentResponse(Objects.requireNonNull(owner.individualRequestId()),
                Objects.requireNonNull(owner.participantId()), details);
    }
}
