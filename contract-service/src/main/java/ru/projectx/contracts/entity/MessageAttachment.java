package ru.projectx.contracts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "message_attachments")
public class MessageAttachment {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "outbox_message_id", nullable = false, updatable = false)
    private UUID outboxMessageId;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "file_key", nullable = false, updatable = false)
    private String fileKey;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "file_size", nullable = false, updatable = false)
    private long fileSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MessageAttachment() {
    }

    private MessageAttachment(UUID outboxMessageId, UUID documentId, StoredFile file, Instant now) {
        this.outboxMessageId = outboxMessageId;
        this.documentId = documentId;
        this.fileKey = file.key();
        this.contentType = file.contentType();
        this.fileSize = file.size();
        this.createdAt = now;
    }

    public static MessageAttachment of(UUID outboxMessageId, UUID documentId, StoredFile file, Instant now) {
        return new MessageAttachment(outboxMessageId, documentId, file, now);
    }

    public UUID documentId() {
        return documentId;
    }

    public String fileKey() {
        return fileKey;
    }

    public String contentType() {
        return contentType;
    }

    public long fileSize() {
        return fileSize;
    }
}
