package ru.projectx.contracts.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "issuance_requests")
public class IssuanceRequest {

    private static final int NO_SNAPSHOT = 0;

    @Id
    @Column(name = "individual_request_id", nullable = false, updatable = false)
    private UUID individualRequestId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "request_participants", joinColumns = @JoinColumn(name = "individual_request_id"))
    @OrderColumn(name = "position", nullable = false)
    private List<RequestParticipant> participants = new ArrayList<>();

    @Column(name = "snapshot_version", nullable = false)
    private int snapshotVersion;

    @Nullable
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IssuanceRequest() {
    }

    private IssuanceRequest(UUID individualRequestId, Instant now) {
        this.individualRequestId = individualRequestId;
        this.snapshotVersion = NO_SNAPSHOT;
        this.updatedAt = now;
    }

    public static IssuanceRequest start(UUID individualRequestId, Instant now) {
        return new IssuanceRequest(individualRequestId, now);
    }

    public void takeComposition(List<RequestParticipant> composition, Instant now) {
        this.participants.clear();
        this.participants.addAll(composition);
        this.cancelledAt = null;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        this.cancelledAt = now;
        this.updatedAt = now;
    }

    public int nextSnapshotVersion(Instant now) {
        this.snapshotVersion += 1;
        this.updatedAt = now;
        return snapshotVersion;
    }

    public UUID individualRequestId() {
        return individualRequestId;
    }

    public List<RequestParticipant> participants() {
        return List.copyOf(participants);
    }

    public boolean cancelled() {
        return cancelledAt != null;
    }

    public int snapshotVersion() {
        return snapshotVersion;
    }
}
