package ru.projectx.contracts.entity.inbox;

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
import ru.projectx.contracts.entity.MessageEnvelope;

@Entity
@Table(name = "inbox_messages")
public class InboxMessage extends JournalMessage {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private InboxStatus status;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Nullable
    @Column(name = "processed_at")
    private Instant processedAt;

    protected InboxMessage() {
    }

    private InboxMessage(InboxReceipt receipt, String payload, Instant receivedAt) {
        super(new MessageEnvelope(receipt.messageId(), receipt.correlationId(), receipt.topic(), receipt.eventType(),
                receipt.entityVersion()), payload);
        this.status = InboxStatus.NEW;
        this.receivedAt = receivedAt;
    }

    public static InboxMessage receive(InboxReceipt receipt, String payload, Instant receivedAt) {
        return new InboxMessage(receipt, payload, receivedAt);
    }

    public void markProcessed(Instant now) {
        requirePending();
        this.status = InboxStatus.PROCESSED;
        this.processedAt = now;
        settle();
    }

    public void markSkipped(String reason, Instant now) {
        requirePending();
        this.status = InboxStatus.SKIPPED;
        this.processedAt = now;
        settle(reason);
    }

    public void markFailed(String reason) {
        requirePending();
        this.status = InboxStatus.FAILED;
        countFailedAttempt(reason, null);
    }

    @Override
    protected boolean pending() {
        return status == InboxStatus.NEW;
    }

    public UUID id() {
        return id;
    }

    public InboxReceipt receipt() {
        MessageEnvelope envelope = envelope();
        return new InboxReceipt(envelope.messageId(), envelope.correlationId(), envelope.topic(), envelope.eventType(),
                envelope.entityVersion());
    }

    @Override
    public InboxStatus status() {
        return status;
    }
}
