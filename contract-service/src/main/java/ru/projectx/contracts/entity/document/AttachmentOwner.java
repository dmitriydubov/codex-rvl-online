package ru.projectx.contracts.entity.document;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Embeddable
public record AttachmentOwner(
        @Column(name = "individual_request_id", updatable = false) @Nullable UUID individualRequestId,
        @Column(name = "participant_id", updatable = false) @Nullable UUID participantId,
        @Column(name = "policy_id", updatable = false) @Nullable UUID policyId,
        @Column(name = "family_member_id", updatable = false) @Nullable UUID familyMemberId,
        @Column(name = "attachment_id", updatable = false) UUID attachmentId) {
}
