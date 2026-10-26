package ru.projectx.contracts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

@MappedSuperclass
public abstract class IssuanceRecord {

    private static final int FIRST_VERSION = 1;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Embedded
    private RequestReference request;

    @Column(name = "policyholder_participant_id", nullable = false)
    private UUID policyholderParticipantId;

    @Nullable
    @Column(name = "program_id")
    private UUID programId;

    @Embedded
    private TripPeriod period;

    @Nullable
    @Embedded
    private IssueError error;

    @Nullable
    @Embedded
    private Cancellation cancellation;

    @Column(name = "entity_version", nullable = false)
    private int entityVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IssuanceRecord() {
    }

    protected IssuanceRecord(IssuanceTerms terms, Instant now) {
        this.request = terms.request();
        this.policyholderParticipantId = terms.policyholderParticipantId();
        this.programId = terms.programId();
        this.period = terms.period();
        this.entityVersion = FIRST_VERSION;
        this.createdAt = now;
        this.updatedAt = now;
    }

    protected abstract void enter(IssuanceTransition transition);

    public abstract IssuanceStatus status();

    public abstract UUID participantId();

    public boolean finished() {
        return status().finished();
    }

    public void submit(Instant now) {
        enter(IssuanceTransition.SUBMITTED);
        advanceVersion(now);
    }

    public void markError(IssueError failure, Instant now) {
        this.error = failure;
        enter(IssuanceTransition.ERROR);
        advanceVersion(now);
    }

    public void cancel(Cancellation reason, Instant now) {
        this.cancellation = reason;
        enter(IssuanceTransition.CANCELLED);
        advanceVersion(now);
    }

    public boolean movePeriod(TripPeriod newPeriod) {
        if (period.equals(newPeriod)) {
            return false;
        }
        this.period = newPeriod;
        return true;
    }

    public boolean moveProgram(UUID newProgramId) {
        if (newProgramId.equals(programId)) {
            return false;
        }
        this.programId = newProgramId;
        return true;
    }

    public void advanceVersion(Instant now) {
        this.entityVersion += 1;
        this.updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public IssuanceState state() {
        return new IssuanceState(request, policyholderParticipantId, Optional.ofNullable(programId), period,
                Optional.ofNullable(error), Optional.ofNullable(cancellation), entityVersion, updatedAt);
    }
}
