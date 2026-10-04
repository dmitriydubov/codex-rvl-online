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
 * Сообщение 1С:УСК, тело которого база данных не принимает в сырьё: лежит в карантине байт в байт, с именем
 * очереди-источника и причиной отказа.
 *
 * <p>Вернуть такое сообщение брокеру нельзя: оно выдавалось бы снова первым и держало бы очередь целиком. В сырьё
 * его тоже не положить — колонка {@code jsonb} отвергает, например, JSON-escape нулевого символа. Поэтому тело
 * сохраняется отдельно, как байты, и сообщение подтверждается брокеру. Разобрать его может только человек:
 * автоматическая переобработка карантина не предусмотрена.
 *
 * <p>Причина — код SQLSTATE отказа, без текста ошибки: текст ошибки базы печатает значения строки, а этим путём
 * идут и полисы с персональными данными (ПДн). По той же причине у тела есть срок хранения: оно не разобрано, и
 * по имени очереди нельзя поручиться, что ПДн в нём нет. По истечении срока тело стирается, запись с причиной
 * остаётся — по ней видно, что сообщение приходило и чем кончилось.
 *
 * <p>Ключ — {@code message-id} сообщения: повторная доставка уже карантинного сообщения второй записи не создаёт.
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
