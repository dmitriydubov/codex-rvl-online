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

/**
 * Сообщение 1С целиком, как оно пришло, вместе с исходом его обработки.
 *
 * <p>Сообщение хранится до разбора, а не разбирается прямо при приёме: так приём не зависит от разбора.
 * Сообщение, которое не удалось разложить по таблицам, остаётся в базе полностью. Его можно посмотреть,
 * исправить разбор и обработать заново обычным SQL, не запрашивая выгрузку из 1С ещё раз.
 *
 * <p>Таблица называется {@code inbound_message}, потому что в ней лежат входящие сообщения. Ключ строки —
 * идентификатор сообщения 1С, как и у журнала входящих: на одно сообщение приходится одна строка, а повторные
 * доставки сюда не доходят, их отсекает журнал. Внешнего ключа между двумя таблицами нет намеренно: строка
 * журнала маленькая и хранится всегда, а тело сообщения здесь со временем стирается.
 *
 * <p>Сеттеров нет. Состояние меняется только именованными переходами вроде {@link #markProcessed} и
 * {@link #markFailed}, и каждый записывает исход целиком. Для чтения сообщение отдаёт три снимка: сведения
 * о приёме, исход обработки вместе с ожиданием родителя и место в передаче из нескольких сообщений.
 */
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

    /**
     * Тело сообщения в колонке {@code jsonb}: его разбирают запросами по содержимому, и приводить текст к JSON
     * в каждом таком запросе было бы лишней работой.
     *
     * <p>Хранится содержимое, а не исходный текст. PostgreSQL складывает {@code jsonb} в своём виде: пробелы
     * и переносы теряются, порядок ключей не сохраняется, из повторяющихся ключей остаётся последний. Разбору
     * это безразлично, он читает поля по имени. Но побайтовое сравнение с тем, что прислала 1С, не сойдётся.
     *
     * <p>Пустое значение означает, что тело стёрто по сроку хранения, а строка осталась. Тело пишется только при
     * приёме: в обновление строки оно не входит, поэтому сохранение строки, прочитанной до стирания, тело не вернёт.
     */
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

    /**
     * Текст последнего отказа или причина пропуска. Оба смысла живут в одной колонке, потому что читаются
     * всегда вместе со статусом.
     */
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

    /** Когда сообщение начало ждать родителя. У отказа по истечении ожидания остаётся: по нему стирается тело. */
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
        // JPA
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

    /** Новое сообщение со статусом {@code NEW}, ещё без попыток обработки. */
    public static InboundMessage receive(InboundReceipt receipt, String payload) {
        return new InboundMessage(receipt, payload);
    }

    /**
     * Сообщение — часть передачи, которая ещё не собралась. Разметка передачи сохраняется здесь же: по ней
     * сборщик находит остальные сообщения передачи, не разбирая тела заново.
     *
     * <p>Часть, вручную возвращённая в разбор, забывает прежнее ожидание услуги: иначе собранная заново передача
     * истекла бы в том же проходе.
     *
     * @param batchCount сколько пакетов строк ожидается; есть только у финального сообщения, у пакетов пусто
     * @param rowCount   строк в этом пакете, а у финального сообщения — сколько их ожидается во всей передаче
     */
    public void markBuffered(UUID correlationId, @Nullable Integer batchCount, int rowCount) {
        this.parentWaitSince = status == InboundStatus.NEW ? null : parentWaitSince;
        this.status = InboundStatus.BUFFERED;
        this.correlationId = correlationId;
        this.batchCount = batchCount;
        this.rowCount = rowCount;
        this.nextRetryAt = null;
    }

    /** Сообщение разложено по таблицам. */
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

    /**
     * Сообщение не прошло отбор своего потока. Причина обязательна: без неё справочник, пустой из-за отсутствия
     * данных, в базе не отличить от справочника, пустого из-за неверной настройки отбора.
     */
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

    /** Временная ошибка: попытка засчитана, следующая назначена через задержку из настроенного списка повторов. */
    public void markRetrying(String error, Instant nextRetryAt) {
        this.status = InboundStatus.RETRY;
        this.attempt += 1;
        this.lastError = error;
        this.nextRetryAt = nextRetryAt;
    }

    /** Постоянная ошибка или исчерпанный список повторов: больше попыток не будет. */
    public void markFailed(String error) {
        this.status = InboundStatus.FAILED;
        this.lastError = error;
        this.nextRetryAt = null;
        this.parentWaitSince = null;
        this.awaitedParentKind = null;
        this.awaitedParentId = null;
        this.recordExternalId = null;
    }

    /**
     * Сообщение ссылается на запись, которой ещё нет, и ждёт её. Сообщение, которое после пересмотра снова ждёт,
     * например головную организацию вместо отделения, отсчитывает срок с первого ожидания. Сообщение, вручную
     * возвращённое в разбор, начинает ожидание заново: иначе прежний срок отклонил бы его в том же проходе.
     *
     * @param record GUID записи справочника, которую меняет сообщение; пусто у передачи прайса
     */
    public void markAwaitingParent(AwaitedParent parent, @Nullable UUID record, Instant now) {
        this.parentWaitSince = status == InboundStatus.NEW ? now : Objects.requireNonNullElse(parentWaitSince, now);
        this.status = InboundStatus.AWAITING_PARENT;
        this.lastError = parent.waitingReason();
        this.nextRetryAt = null;
        this.awaitedParentKind = parent.kind();
        this.awaitedParentId = parent.id();
        this.recordExternalId = record;
    }

    /**
     * Родитель не пришёл за срок ожидания: окончательный отказ с причиной, которая его называет. Ожидание в строке
     * остаётся: по нему такой отказ отличается от прочих, и тело стирается по сроку хранения.
     *
     * @throws IllegalStateException если сообщение ничего не ждёт
     */
    public void markParentWaitExpired() {
        AwaitedParent parent = outcome().parentWait()
                .orElseThrow(() -> new IllegalStateException("сообщение " + messageId + " не ждёт родителя"))
                .parent();
        this.status = InboundStatus.FAILED;
        this.lastError = parent.expiredReason();
        this.nextRetryAt = null;
    }

    /** Что пришло, по какому потоку, из какой очереди и когда. */
    public InboundReceipt receipt() {
        return new InboundReceipt(messageId, stream, sourceQueue, receivedAt);
    }

    /** Тело сообщения; пусто, если оно стёрто по сроку хранения. */
    @Nullable
    public String payload() {
        return payload;
    }

    /**
     * Исход обработки на текущий момент. Сообщение ждёт или ждало родителя, если задан момент начала ожидания: вид и
     * GUID родителя записывает вместе с ним только один переход.
     */
    public InboundOutcome outcome() {
        ParentWait awaited = parentWaitSince == null ? null : new ParentWait(
                new AwaitedParent(Objects.requireNonNull(awaitedParentKind), Objects.requireNonNull(awaitedParentId)),
                parentWaitSince, recordExternalId);
        return new InboundOutcome(status, attempt, nextRetryAt, lastError, processedAt, awaited);
    }

    /** Место сообщения в передаче из нескольких сообщений; у остальных сообщений все значения пустые. */
    public InboundTransferPart transferPart() {
        return new InboundTransferPart(correlationId, batchCount, rowCount);
    }

}
