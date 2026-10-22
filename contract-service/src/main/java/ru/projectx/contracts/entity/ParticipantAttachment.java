package ru.projectx.contracts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
public record ParticipantAttachment(
        @Column(name = "individual_request_id", updatable = false) UUID individualRequestId,
        @Column(name = "participant_id", updatable = false) UUID participantId,
        @Column(name = "attachment_id", updatable = false) UUID attachmentId) {
}
