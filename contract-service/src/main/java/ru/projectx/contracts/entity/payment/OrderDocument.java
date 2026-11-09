package ru.projectx.contracts.entity.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.time.Instant;

@Embeddable
public record OrderDocument(
        @Column(name = "contract_number", nullable = false, updatable = false) String contractNumber,
        @Column(name = "amount", nullable = false, updatable = false) BigDecimal amount,
        @Column(name = "document_date", nullable = false, updatable = false) Instant documentDate,
        @Column(name = "document_ttl", nullable = false, updatable = false) Instant documentTtl) {
}
