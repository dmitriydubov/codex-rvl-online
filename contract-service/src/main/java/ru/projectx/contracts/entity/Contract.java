package ru.projectx.contracts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "contracts")
public class Contract extends IssuanceRecord {

    @Column(nullable = false, updatable = false)
    private String number;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private ContractStatus status;

    @Column(name = "insured_participant_id", nullable = false, updatable = false)
    private UUID insuredParticipantId;

    @Column(name = "premium_amount", nullable = false)
    private BigDecimal premiumAmount;

    protected Contract() {
    }

    private Contract(ContractDraft draft, Instant now) {
        super(draft.request(), draft.policyholderParticipantId(), draft.period(), now);
        this.number = draft.number();
        this.status = ContractStatus.NEW;
        this.insuredParticipantId = draft.insuredParticipantId();
        this.premiumAmount = draft.premium();
    }

    public static Contract open(ContractDraft draft, Instant now) {
        return new Contract(draft, now);
    }

    public void revise(ContractTerms terms, Instant now) {
        movePeriod(terms.period(), now);
        if (premiumAmount.compareTo(terms.premium()) != 0) {
            this.premiumAmount = terms.premium();
            bump(now);
        }
    }

    @Override
    protected void enterError() {
        this.status = ContractStatus.ERROR;
    }

    @Override
    protected void enterCancelled() {
        this.status = ContractStatus.CANCELLED;
    }

    public String number() {
        return number;
    }

    @Override
    public ContractStatus status() {
        return status;
    }

    public UUID insuredParticipantId() {
        return insuredParticipantId;
    }

    public BigDecimal premiumAmount() {
        return premiumAmount;
    }
}
