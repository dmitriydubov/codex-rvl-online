package ru.projectx.contracts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Embeddable
public record RequestParticipant(
        @Column(name = "participant_id", nullable = false) UUID participantId,
        @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
        @Column(name = "document_kind", nullable = false) DocumentKind kind) {
}
