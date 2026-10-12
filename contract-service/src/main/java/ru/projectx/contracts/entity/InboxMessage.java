package ru.projectx.contracts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "inbox_messages")
public class InboxMessage {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "correlation_id", nullable = false, updatable = false)
    private UUID correlationId;

    @Column(nullable = false, updatable = false)
    private String topic;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "entity_version", nullable = false, updatable = false)
    private int entityVersion;

    @Nullable
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private InboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Nullable
    @Column(name = "error_message")
    private String errorMessage;

    @Nullable
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Nullable
    @Column(name = "processed_at")
    private Instant processedAt;

    protected InboxMessage() {
    }

    private InboxMessage(InboxReceipt receipt, String payload, Instant receivedAt) {
        this.messageId = receipt.messageId();
        this.correlationId = receipt.correlationId();
        this.topic = receipt.topic();
        this.eventType = receipt.eventType();
        this.entityVersion = receipt.entityVersion();
        this.payload = payload;
        this.status = InboxStatus.NEW;
        this.attempts = 0;
        this.receivedAt = receivedAt;
    }

    public static InboxMessage receive(InboxReceipt receipt, String payload, Instant receivedAt) {
        return new InboxMessage(receipt, payload, receivedAt);
    }

    public void markProcessed(Instant now) {
        requirePending();
        this.status = InboxStatus.PROCESSED;
        this.processedAt = now;
        this.nextAttemptAt = null;
    }

    public void markSkipped(String reason, Instant now) {
        requirePending();
        this.status = InboxStatus.SKIPPED;
        this.errorMessage = reason;
        this.processedAt = now;
        this.nextAttemptAt = null;
    }

    public void markRetry(String reason, Instant nextAttemptAt) {
        requirePending();
        this.attempts += 1;
        this.errorMessage = reason;
        this.nextAttemptAt = nextAttemptAt;
    }

    public void markFailed(String reason) {
        requirePending();
        this.attempts += 1;
        this.status = InboxStatus.FAILED;
        this.errorMessage = reason;
        this.nextAttemptAt = null;
    }

    private void requirePending() {
        if (status != InboxStatus.NEW) {
            throw new IllegalStateException("исход сообщения " + messageId + " уже зафиксирован: " + status);
        }
    }

    public UUID id() {
        return id;
    }

    public InboxReceipt receipt() {
        return new InboxReceipt(messageId, correlationId, topic, eventType, entityVersion);
    }

    public Optional<String> payload() {
        return Optional.ofNullable(payload);
    }

    public InboxStatus status() {
        return status;
    }

    public int attempts() {
        return attempts;
    }

    public Optional<String> errorMessage() {
        return Optional.ofNullable(errorMessage);
    }
}
