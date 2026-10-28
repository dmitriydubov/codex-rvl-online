package ru.projectx.contracts.entity.issuance;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Embeddable
public record Cancellation(
        @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM) @Column(name = "cancel_reason")
        CancelReason reason,
        @Nullable @Column(name = "cancel_comment") String comment,
        @Column(name = "cancelled_at") Instant cancelledAt) {
}
