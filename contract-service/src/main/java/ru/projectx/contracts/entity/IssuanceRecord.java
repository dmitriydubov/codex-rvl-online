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

    protected IssuanceRecord(RequestReference request, UUID policyholderParticipantId, TripPeriod period, Instant now) {
        this.request = request;
        this.policyholderParticipantId = policyholderParticipantId;
        this.period = period;
        this.entityVersion = FIRST_VERSION;
        this.createdAt = now;
        this.updatedAt = now;
    }

    protected abstract void enterError();

    protected abstract void enterCancelled();

    public abstract IssuanceStatus status();

    public boolean finished() {
        return status().finished();
    }

    public void markError(IssueError failure, Instant now) {
        this.error = failure;
        enterError();
        bump(now);
    }

    public void cancel(Cancellation reason, Instant now) {
        this.cancellation = reason;
        enterCancelled();
        bump(now);
    }

    public boolean movePeriod(TripPeriod newPeriod, Instant now) {
        if (period.equals(newPeriod)) {
            return false;
        }
        this.period = newPeriod;
        bump(now);
        return true;
    }

    public void markPersonsReplaced(Instant now) {
        bump(now);
    }

    protected void bump(Instant now) {
        this.entityVersion += 1;
        this.updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public IssuanceState state() {
        return new IssuanceState(request, policyholderParticipantId, period, Optional.ofNullable(error),
                Optional.ofNullable(cancellation), entityVersion);
    }
}
