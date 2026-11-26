package ru.projectx.contracts.service.storage;

import java.util.UUID;
import ru.projectx.contracts.entity.document.AttachmentOwner;
import ru.projectx.contracts.entity.document.AttachmentOwners;

public final class AttachmentUpload {

    private final AttachmentOwner owner;
    private final AttachmentContent file;

    private AttachmentUpload(AttachmentOwner owner, AttachmentContent file) {
        this.owner = owner;
        this.file = file;
    }

    public static AttachmentUpload certificate(UUID request, UUID participant, UUID attachment, AttachmentContent file) {
        return new AttachmentUpload(AttachmentOwners.certificate(request, participant, attachment), file);
    }

    public static AttachmentUpload consent(UUID policy, UUID familyMember, UUID attachment, AttachmentContent file) {
        return new AttachmentUpload(AttachmentOwners.consent(policy, familyMember, attachment), file);
    }

    public AttachmentOwner owner() {
        return owner;
    }

    public AttachmentContent file() {
        return file;
    }
}
