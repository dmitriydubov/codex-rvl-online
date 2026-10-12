package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "foreign_reference")
public class ForeignReference {

    @Id
    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ParentKind kind;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected ForeignReference() {
    }
}
