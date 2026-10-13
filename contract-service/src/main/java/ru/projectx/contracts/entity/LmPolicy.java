package ru.projectx.contracts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "lm_policies")
public class LmPolicy extends IssuanceRecord {

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private LmPolicyStatus status;

    @Column(name = "participant_id", nullable = false, updatable = false)
    private UUID participantId;

    @Nullable
    @Column(name = "issued_policy_id")
    private UUID issuedPolicyId;

    @Nullable
    @Column(name = "issued_policy_number")
    private String issuedPolicyNumber;

    @Nullable
    @Column(name = "policy_usk_id")
    private UUID policyUskId;

    @Nullable
    @Column(name = "policy_usk_number")
    private String policyUskNumber;

    protected LmPolicy() {
    }

    private LmPolicy(LmPolicyDraft draft, Instant now) {
        super(draft.terms(), now);
        this.status = LmPolicyStatus.NEW;
        this.participantId = draft.participantId();
    }

    public static LmPolicy open(LmPolicyDraft draft, Instant now) {
        return new LmPolicy(draft, now);
    }

    public void submit(Instant now) {
        require(LmPolicyStatus.NEW);
        this.status = LmPolicyStatus.SUBMITTED;
        advanceVersion(now);
    }

    public void markIssued(IssuedPolicy issued, Instant now) {
        require(LmPolicyStatus.SUBMITTED);
        this.status = LmPolicyStatus.ISSUED;
        this.policyUskId = issued.policyUskId();
        this.policyUskNumber = issued.policyUskNumber();
        this.issuedPolicyId = issued.issuedPolicyId();
        this.issuedPolicyNumber = issued.issuedPolicyNumber();
        advanceVersion(now);
    }

    private void require(LmPolicyStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("полис ЛМ " + id() + " в статусе " + status + ", а не " + expected);
        }
    }

    @Override
    protected void enterError() {
        this.status = LmPolicyStatus.ERROR;
    }

    @Override
    protected void enterCancelled() {
        this.status = LmPolicyStatus.CANCELLED;
    }

    public boolean submitted() {
        return status == LmPolicyStatus.SUBMITTED;
    }

    @Override
    public LmPolicyStatus status() {
        return status;
    }

    @Override
    public UUID participantId() {
        return participantId;
    }

    public Optional<IssuedPolicy> issued() {
        if (status != LmPolicyStatus.ISSUED) {
            return Optional.empty();
        }
        return Optional.of(new IssuedPolicy(policyUskId, policyUskNumber, issuedPolicyId, issuedPolicyNumber));
    }
}
