package ru.projectx.contracts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@MappedSuperclass
public abstract class JournalMessage {

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

    @Column(nullable = false)
    private int attempts;

    @Nullable
    @Column(name = "error_message")
    private String errorMessage;

    @Nullable
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    protected JournalMessage() {
    }

    protected JournalMessage(MessageEnvelope envelope, @Nullable String payload) {
        this.messageId = envelope.messageId();
        this.correlationId = envelope.correlationId();
        this.topic = envelope.topic();
        this.eventType = envelope.eventType();
        this.entityVersion = envelope.entityVersion();
        this.payload = payload;
        this.attempts = 0;
    }

    protected abstract boolean pending();

    protected abstract Enum<?> status();

    protected void requirePending() {
        if (!pending()) {
            throw new IllegalStateException("исход сообщения " + messageId + " уже зафиксирован: " + status());
        }
    }

    public void markRetry(String reason, Instant nextAttemptAt) {
        requirePending();
        countFailedAttempt(reason, nextAttemptAt);
    }

    protected void countAttempt() {
        this.attempts += 1;
    }

    protected void countFailedAttempt(String reason, @Nullable Instant retryAt) {
        this.attempts += 1;
        this.errorMessage = reason;
        this.nextAttemptAt = retryAt;
    }

    protected void settle() {
        this.nextAttemptAt = null;
    }

    protected void settle(String reason) {
        this.errorMessage = reason;
        this.nextAttemptAt = null;
    }

    public MessageEnvelope envelope() {
        return new MessageEnvelope(messageId, correlationId, topic, eventType, entityVersion);
    }

    public Optional<String> payload() {
        return Optional.ofNullable(payload);
    }

    public int attempts() {
        return attempts;
    }

    public Optional<String> errorMessage() {
        return Optional.ofNullable(errorMessage);
    }
}
