package ru.projectx.contracts.entity.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "inbox_quarantine")
public class QuarantinedMessage {

    @Id
    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "source_queue", nullable = false, updatable = false)
    private String sourceQueue;

    @Nullable
    @Column(columnDefinition = "bytea")
    private byte[] body;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected QuarantinedMessage() {
    }

    private QuarantinedMessage(RejectedMessage rejected, String reason, Instant receivedAt) {
        this.messageId = rejected.messageId();
        this.sourceQueue = rejected.sourceQueue();
        this.body = rejected.body().clone();
        this.reason = reason;
        this.receivedAt = receivedAt;
    }

    public static QuarantinedMessage of(RejectedMessage rejected, String reason, Instant receivedAt) {
        return new QuarantinedMessage(rejected, reason, receivedAt);
    }

    public UUID messageId() {
        return messageId;
    }

    public String sourceQueue() {
        return sourceQueue;
    }

    public Optional<byte[]> body() {
        return Optional.ofNullable(body).map(byte[]::clone);
    }

    public String reason() {
        return reason;
    }

    public Instant receivedAt() {
        return receivedAt;
    }
}
