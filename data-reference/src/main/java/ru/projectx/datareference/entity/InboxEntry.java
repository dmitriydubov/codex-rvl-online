package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Запись журнала входящих: «сообщение с таким {@code message-id} мы уже принимали».
 *
 * <p>У таблицы ровно одна работа — <b>дедупликация</b>. Тела сообщения здесь нет
 * намеренно: сырьё хранит {@code inbound_message}, и смешивать две задачи в одной таблице значит
 * получить строку, у которой половина полей всегда пустая. Отсюда и две колонки: ключ
 * дедупликации и время приёма.
 *
 * <p>Ключ таблицы — сам {@code message_id}, без суррогатного {@code id}. Он приходит
 * из {@code metadata.message-id} сообщения 1С, это GUID, и он же — единственное, по чему
 * строки различаются. Отдельный суррогат добавил бы колонку и индекс, не добавив ничего
 * к смыслу.
 *
 * <p>Сеттеров нет: запись создаётся фабрикой и больше не меняется. Принятое сообщение —
 * свершившийся факт, менять в нём нечего.
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
