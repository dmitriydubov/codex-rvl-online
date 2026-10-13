package ru.projectx.contracts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "lm_policies")
public class LmPolicy extends IssuanceRecord {

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private LmPolicyStatus status;

    @Column(name = "participant_id", nullable = false, updatable = false)
    private UUID participantId;

    protected LmPolicy() {
    }

    private LmPolicy(LmPolicyDraft draft, Instant now) {
        super(draft.request(), draft.policyholderParticipantId(), draft.period(), now);
        this.status = LmPolicyStatus.NEW;
        this.participantId = draft.participantId();
    }

    public static LmPolicy open(LmPolicyDraft draft, Instant now) {
        return new LmPolicy(draft, now);
    }

    @Override
    protected void enterError() {
        this.status = LmPolicyStatus.ERROR;
    }

    @Override
    protected void enterCancelled() {
        this.status = LmPolicyStatus.CANCELLED;
    }

    @Override
    public LmPolicyStatus status() {
        return status;
    }

    @Override
    public UUID participantId() {
        return participantId;
    }
}
