package ru.projectx.contracts.entity.document;

import java.util.UUID;

public final class AttachmentOwners {

    private AttachmentOwners() {
    }

    public static AttachmentOwner certificate(UUID request, UUID participant, UUID attachment) {
        return new AttachmentOwner(request, participant, null, null, attachment);
    }

    public static AttachmentOwner consent(UUID policy, UUID familyMember, UUID attachment) {
        return new AttachmentOwner(null, null, policy, familyMember, attachment);
    }

    public static PrintedFormType documentType(AttachmentOwner owner) {
        return owner.individualRequestId() != null ? PrintedFormType.MEDICAL_CERTIFICATE : PrintedFormType.PD_CONSENT;
    }
}
