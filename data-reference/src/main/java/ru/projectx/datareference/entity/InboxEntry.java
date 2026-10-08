package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Отметка, что сообщение с таким {@code message-id} уже принималось: таблица нужна только для дедупликации.
 * Тело хранит {@code inbound_message}, а ключом служит сам GUID сообщения из 1С без суррогатного идентификатора.
 */
@Entity
@Table(name = "inbox")
public class InboxEntry {

    @Id
    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected InboxEntry() {
        // JPA
    }

    private InboxEntry(UUID messageId, Instant receivedAt) {
        this.messageId = messageId;
        this.receivedAt = receivedAt;
    }

    public static InboxEntry receive(UUID messageId, Instant receivedAt) {
        return new InboxEntry(messageId, receivedAt);
    }

    public UUID messageId() {
        return messageId;
    }

    public Instant receivedAt() {
        return receivedAt;
    }
}
