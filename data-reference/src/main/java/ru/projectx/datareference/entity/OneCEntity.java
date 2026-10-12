package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public class OneCEntity {

    @Id
    private UUID id;

    @Column(name = "external_id", nullable = false, unique = true, updatable = false)
    private UUID externalId;

    @Column(name = "is_archive", nullable = false)
    private boolean archive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OneCEntity() {
    }

    protected OneCEntity(UUID externalId, Instant now) {
        this.id = UUID.randomUUID();
        this.externalId = externalId;
        this.archive = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    protected void applyCommon(boolean deleteMark, Instant now) {
        this.archive = deleteMark;
        this.updatedAt = now;
    }

    protected void archiveCommon(Instant now) {
        applyCommon(true, now);
    }

    public UUID id() {
        return id;
    }

    public UUID externalId() {
        return externalId;
    }

    public boolean isArchive() {
        return archive;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
