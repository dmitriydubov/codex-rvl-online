package ru.projectx.contracts.entity.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import ru.projectx.contracts.entity.JournalMessage;

@Entity
@Table(name = "outbox_messages")
public class OutboxMessage extends JournalMessage {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private OutboxStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Nullable
    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxMessage() {
    }

    private OutboxMessage(OutboxDraft draft, Instant now) {
        super(draft.envelope(), draft.payload());
        this.status = OutboxStatus.NEW;
        this.aggregateType = draft.aggregateType();
        this.aggregateId = draft.aggregateId();
        this.createdAt = now;
    }

    public static OutboxMessage compose(OutboxDraft draft, Instant now) {
        return new OutboxMessage(draft, now);
    }

    public void markPublished(Instant now) {
        requirePending();
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = now;
        countAttempt();
        settle();
    }

    public void markFailed(String reason) {
        requirePending();
        this.status = OutboxStatus.FAILED;
        countFailedAttempt(reason, null);
    }

    @Override
    protected boolean pending() {
        return status == OutboxStatus.NEW;
    }

    public UUID id() {
        return id;
    }

    public String aggregateType() {
        return aggregateType;
    }

    public UUID aggregateId() {
        return aggregateId;
    }

    @Override
    public OutboxStatus status() {
        return status;
    }
}
