package ru.projectx.contracts.entity.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Embeddable
public record OfferAcceptance(
        @Column(name = "contract_version", nullable = false, updatable = false) int contractVersion,
        @Column(name = "accepted_by", nullable = false, updatable = false) UUID acceptedBy,
        @Column(name = "accepted_at", nullable = false, updatable = false) Instant acceptedAt,
        @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
        @Column(name = "channel", nullable = false, updatable = false) AcceptanceChannel channel,
        @Nullable @JdbcTypeCode(SqlTypes.INET)
        @Column(name = "ip_address", updatable = false) InetAddress ipAddress,
        @Nullable @Column(name = "user_agent", updatable = false) String userAgent) {
}
