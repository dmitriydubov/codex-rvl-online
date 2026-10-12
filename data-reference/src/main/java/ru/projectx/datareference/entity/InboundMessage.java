package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "inbound_message")
public class InboundMessage {

    @Id
    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OneCStream stream;

    @Column(name = "source_queue", nullable = false, updatable = false)
    private String sourceQueue;

    @Nullable
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InboundStatus status;

    @Column(nullable = false)
    private int attempt;

    @Nullable
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Nullable
    @Column(name = "last_error")
    private String lastError;

    @Nullable
    @Column(name = "correlation_id")
    private UUID correlationId;

    @Nullable
    @Column(name = "batch_count")
    private Integer batchCount;

    @Nullable
    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Nullable
    @Column(name = "processed_at")
    private Instant processedAt;

    @Nullable
    @Column(name = "parent_wait_since")
    private Instant parentWaitSince;

    @Nullable
    @Enumerated(EnumType.STRING)
    @Column(name = "awaited_parent_kind")
    private ParentKind awaitedParentKind;

    @Nullable
    @Column(name = "awaited_parent_id")
    private UUID awaitedParentId;

    @Nullable
    @Column(name = "record_external_id")
    private UUID recordExternalId;

    protected InboundMessage() {
    }

    private InboundMessage(InboundReceipt receipt, String payload) {
        this.messageId = receipt.messageId();
        this.stream = receipt.stream();
        this.sourceQueue = receipt.sourceQueue();
        this.receivedAt = receipt.receivedAt();
        this.payload = payload;
        this.status = InboundStatus.NEW;
        this.attempt = 0;
    }

    public static InboundMessage receive(InboundReceipt receipt, String payload) {
        return new InboundMessage(receipt, payload);
    }

    public void markBuffered(UUID correlationId, @Nullable Integer batchCount, int rowCount) {
        this.parentWaitSince = status == InboundStatus.NEW ? null : parentWaitSince;
        this.status = InboundStatus.BUFFERED;
        this.correlationId = correlationId;
        this.batchCount = batchCount;
        this.rowCount = rowCount;
        this.nextRetryAt = null;
    }

    public void markProcessed(Instant processedAt) {
        this.status = InboundStatus.PROCESSED;
        this.processedAt = processedAt;
        this.nextRetryAt = null;
        this.lastError = null;
        this.parentWaitSince = null;
        this.awaitedParentKind = null;
        this.awaitedParentId = null;
        this.recordExternalId = null;
    }

    public void markSkipped(String reason, Instant processedAt) {
        this.status = InboundStatus.SKIPPED;
        this.lastError = reason;
        this.processedAt = processedAt;
        this.nextRetryAt = null;
        this.parentWaitSince = null;
        this.awaitedParentKind = null;
        this.awaitedParentId = null;
        this.recordExternalId = null;
    }

    public void markRetrying(String error, Instant nextRetryAt) {
        this.parentWaitSince = status == InboundStatus.NEW ? null : parentWaitSince;
        this.status = InboundStatus.RETRY;
        this.attempt += 1;
        this.lastError = error;
        this.nextRetryAt = nextRetryAt;
    }

    public void markFailed(String error) {
        this.status = InboundStatus.FAILED;
        this.lastError = error;
        this.nextRetryAt = null;
        this.parentWaitSince = null;
        this.awaitedParentKind = null;
        this.awaitedParentId = null;
        this.recordExternalId = null;
    }

    public void markAwaitingParent(AwaitedParent parent, @Nullable UUID record, Instant now) {
        this.parentWaitSince = status == InboundStatus.NEW ? now : Objects.requireNonNullElse(parentWaitSince, now);
        this.status = InboundStatus.AWAITING_PARENT;
        this.lastError = parent.waitingReason();
        this.nextRetryAt = null;
        this.awaitedParentKind = parent.kind();
        this.awaitedParentId = parent.id();
        this.recordExternalId = record;
    }

    public void markParentWaitExpired() {
        AwaitedParent parent = outcome().parentWait()
                .orElseThrow(() -> new IllegalStateException("сообщение " + messageId + " не ждёт родителя"))
                .parent();
        this.status = InboundStatus.FAILED;
        this.lastError = parent.expiredReason();
        this.nextRetryAt = null;
    }

    public InboundReceipt receipt() {
        return new InboundReceipt(messageId, stream, sourceQueue, receivedAt);
    }

    @Nullable
    public String payload() {
        return payload;
    }

    public InboundOutcome outcome() {
        ParentWait awaited = parentWaitSince == null ? null : new ParentWait(
                new AwaitedParent(Objects.requireNonNull(awaitedParentKind), Objects.requireNonNull(awaitedParentId)),
                parentWaitSince, recordExternalId);
        return new InboundOutcome(status, attempt, nextRetryAt, lastError, processedAt, awaited);
    }

    public InboundTransferPart transferPart() {
        return new InboundTransferPart(correlationId, batchCount, rowCount);
    }

}
