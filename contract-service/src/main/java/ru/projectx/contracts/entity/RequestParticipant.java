package ru.projectx.contracts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.UUID;

@Embeddable
public record RequestParticipant(
        @Column(name = "participant_id", nullable = false) UUID participantId,
        @Enumerated(EnumType.STRING) @Column(name = "document_kind", nullable = false) DocumentKind kind) {
}
