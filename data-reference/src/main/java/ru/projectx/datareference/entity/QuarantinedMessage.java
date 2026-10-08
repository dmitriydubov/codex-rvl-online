package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Сообщение 1С:УСК, тело которого база не принимает в сырьё (например, {@code jsonb} отвергает нулевой символ).
 * Вернуть его брокеру нельзя, оно держало бы очередь, поэтому тело лежит здесь байтами и разбирается человеком.
 * Причина хранится кодом SQLSTATE без текста, а тело стирается по сроку: в них могут быть персональные данные.
 */
@Entity
@Table(name = "quarantine")
public class QuarantinedMessage {

    @Id
    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private OneCStream stream;

    @Column(name = "source_queue", nullable = false, updatable = false)
    private String sourceQueue;

    /** Тело как прислал брокер; пусто — стёрто по сроку хранения. */
    @Nullable
    @Column(columnDefinition = "bytea")
    private byte[] body;

    /** Код SQLSTATE отказа базы. */
    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected QuarantinedMessage() {
        // JPA
    }

    private QuarantinedMessage(RejectedMessage rejected, String reason, Instant receivedAt) {
        this.messageId = rejected.messageId();
        this.stream = rejected.stream();
        this.sourceQueue = rejected.sourceQueue();
        this.body = rejected.body().clone();
        this.reason = reason;
        this.receivedAt = receivedAt;
    }

    /** Карантинная запись отвергнутого базой сообщения с кодом SQLSTATE отказа. */
    public static QuarantinedMessage of(RejectedMessage rejected, String reason, Instant receivedAt) {
        return new QuarantinedMessage(rejected, reason, receivedAt);
    }

    /** Стирает тело по сроку хранения; запись с причиной остаётся. */
    public void forgetBody() {
        this.body = null;
    }

    public UUID messageId() {
        return messageId;
    }

    public OneCStream stream() {
        return stream;
    }

    public String sourceQueue() {
        return sourceQueue;
    }

    /** Копия тела; пусто — тело стёрто по сроку хранения. */
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
