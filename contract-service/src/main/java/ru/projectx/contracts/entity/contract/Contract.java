package ru.projectx.contracts.entity.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import ru.projectx.contracts.entity.issuance.IssuanceRecord;
import ru.projectx.contracts.entity.issuance.IssuanceTransition;
import ru.projectx.contracts.entity.issuance.IssuedPolicy;

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

    @Nullable
    @Column(name = "paid_at")
    private Instant paidAt;

    @Nullable
    @Embedded
    private ContractRegistration registration;

    protected Contract() {
    }

    private Contract(ContractDraft draft, Instant now) {
        super(draft.terms(), now);
        this.number = draft.number();
        this.status = ContractStatus.NEW;
        this.insuredParticipantId = draft.insuredParticipantId();
        this.premiumAmount = draft.premium();
    }

    public static Contract open(ContractDraft draft, Instant now) {
        return new Contract(draft, now);
    }

    public boolean revise(ContractTerms terms) {
        boolean periodMoved = movePeriod(terms.period());
        if (premiumAmount.compareTo(terms.premium()) == 0) {
            return periodMoved;
        }
        this.premiumAmount = terms.premium();
        return true;
    }

    public void offerDraft() {
        if (status == ContractStatus.NEW) {
            this.status = ContractStatus.DRAFT_READY;
        }
    }

    public void advance(ContractStep step) {
        this.status = step.after(status, id());
    }

    public void markPaid(Instant paidAt, Instant now) {
        if (!status.awaitsPayment()) {
            throw new IllegalStateException("договор КМ " + id() + " в статусе " + status + " не ждёт оплаты");
        }
        this.status = ContractStatus.PAID;
        this.paidAt = paidAt;
        advanceVersion(now);
    }

    public void markIssued(IssuedPolicy issued, Instant now) {
        this.status = status.next(ContractStatus.SUBMITTED, ContractStatus.ISSUED, id());
        this.registration = new ContractRegistration(issued.uskId(), issued.uskNumber(), issued.issuedPolicyId(),
                issued.issuedPolicyNumber());
        advanceVersion(now);
    }

    @Override
    protected void enter(IssuanceTransition transition) {
        this.status = transition.after(ContractStatus.ISSUANCE_FLOW, status, id());
    }

    public String number() {
        return number;
    }

    @Override
    public ContractStatus status() {
        return status;
    }

    @Override
    public UUID participantId() {
        return insuredParticipantId;
    }

    public BigDecimal premiumAmount() {
        return premiumAmount;
    }

    public ContractProgress progress() {
        return new ContractProgress(Optional.ofNullable(paidAt), Optional.ofNullable(registration));
    }
}
