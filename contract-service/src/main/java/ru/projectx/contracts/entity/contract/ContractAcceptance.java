package ru.projectx.contracts.entity.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Immutable
@Table(name = "contract_acceptances")
public class ContractAcceptance {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Embedded
    private OfferAcceptance offer;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ContractAcceptance() {
    }

    private ContractAcceptance(UUID contractId, OfferAcceptance offer, Instant now) {
        this.contractId = contractId;
        this.offer = offer;
        this.createdAt = now;
    }

    public static ContractAcceptance record(UUID contractId, OfferAcceptance offer, Instant now) {
        return new ContractAcceptance(contractId, offer, now);
    }

    public UUID contractId() {
        return contractId;
    }

    public OfferAcceptance offer() {
        return offer;
    }
}
