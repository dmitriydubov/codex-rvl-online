package ru.projectx.datareference.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * Сырое сообщение 1С целиком, со статусом обработки.
 *
 * <p>Зачем сырьё вообще хранится, а не разбирается прямо в слушателе: разбор отделён
 * от приёма. Сообщение, которое не удалось разложить по таблицам, остаётся в базе
 * полностью — его можно посмотреть глазами, починить маппер и переобработать обычным
 * SQL, не запрашивая выгрузку из 1С заново.
 *
 * <p>Таблица <b>входящая</b>, и имя это говорит прямо. Раньше она называлась
 * {@code outbox} — при том, что классический transactional outbox есть очередь
 * <i>исходящих</i> событий. Пока исходящих сообщений не было, расхождение оставалось
 * терпимым; с заявками они появляются, и держать под именем outbox таблицу входящего
 * сырья стало бы прямой ложью.
 *
 * <p>Ключ — {@code message_id} сообщения 1С, как и у {@code inbox}: на одно сообщение
 * приходится ровно одна строка сырья, а дубли до этой таблицы не доходят — их отсекает
 * журнал входящих. Внешнего ключа между таблицами нет намеренно: у них разные задачи
 * и разное время жизни — строка журнала крошечная и живёт вечно, у сырья же тело
 * со временем стирается.
 *
 * <p>Сеттеров нет ни одного: состояние меняется только именованными переходами
 * {@link #markProcessed}, {@link #markSkipped}, {@link #markRetrying} и
 * {@link #markFailed}, каждый из которых записывает исход целиком.
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

    /**
     * Очередь 1С, из которой пришло сообщение. Решения по ней не принимаются — для этого
     * есть {@link #stream}, — но в разборе инцидента она отвечает на вопросы, которых
     * поток не различает: у ТЗ-9 именно она отделяет пакет строк от финального сообщения.
     */
    @Column(name = "source_queue", nullable = false, updatable = false)
    private String sourceQueue;

    /**
     * Тело сообщения. {@code jsonb}, а не {@code text}: сырьё разбирают запросами
     * по содержимому, и приводить его к JSON в каждом таком запросе — лишняя работа
     * и лишняя возможность ошибиться.
     *
     * <p><b>Хранится содержимое, а не исходный текст.</b> {@code jsonb} разбирает
     * документ и складывает в своём виде: пробелы и переносы теряются, порядок ключей
     * не сохраняется, повторяющийся ключ остаётся один — последний. Для разбора и
     * переобработки это безразлично (маппер читает поля по имени), но означает, что
     * побайтовое сравнение с тем, что прислала 1С, не сойдётся. Нужен точный текст
     * ответа источника — это другая задача и другое хранилище.
     *
     * <p>Пусто — тело <b>стёрто по сроку хранения</b>; сама строка при этом остаётся.
     * См. {@link #forgetPayload()}.
     */
    @Nullable
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InboundStatus status;

    /** Сколько раз обработка уже сорвалась временной ошибкой. */
    @Column(nullable = false)
    private int attempt;

    @Nullable
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    /**
     * Текст последнего отказа — либо причина пропуска. Два разных смысла в одной колонке
     * потому, что читаются они всегда вместе со статусом: у {@link InboundStatus#SKIPPED}
     * здесь объяснение фильтра, у {@link InboundStatus#FAILED} — ошибка.
     */
    @Nullable
    @Column(name = "last_error")
    private String lastError;

    /*
     * Три поля ниже нужны только потокам, у которых единица смысла приходит НЕСКОЛЬКИМИ
     * сообщениями. Сегодня такой поток один — прайсы (ТЗ-9). Заполняет их сборщик
     * передачи, а не приём: чтобы узнать эти значения, надо разобрать тело, а приём
     * тела не разбирает. Остальным потокам они остаются пустыми и не мешают.
     */

    /** {@code cid} источника: связывает сообщения одной передачи между собой. */
    @Nullable
    @Column(name = "correlation_id")
    private UUID correlationId;

    /** Сколько сообщений с данными ожидается в передаче. Только у финального сообщения. */
    @Nullable
    @Column(name = "batch_count")
    private Integer batchCount;

    /** Строк в этом сообщении; у финального — сколько строк ожидается во всей передаче. */
    @Nullable
    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Nullable
    @Column(name = "processed_at")
    private Instant processedAt;

    protected InboundMessage() {
        // JPA
    }

    private InboundMessage(UUID messageId, OneCStream stream, String sourceQueue, String payload, Instant receivedAt) {
        this.messageId = messageId;
        this.stream = stream;
        this.sourceQueue = sourceQueue;
        this.payload = payload;
        this.status = InboundStatus.NEW;
        this.attempt = 0;
        this.receivedAt = receivedAt;
    }

    public static InboundMessage receive(
            UUID messageId, OneCStream stream, String sourceQueue, String payload, Instant receivedAt) {

        return new InboundMessage(messageId, stream, sourceQueue, payload, receivedAt);
    }

    /**
     * Сообщение — часть передачи, которая ещё не собралась. Разметка передачи
     * сохраняется здесь же: по ней сборщик находит остальные её сообщения, не разбирая
     * тела заново.
     *
     * @param batchCount сколько сообщений с данными ожидается; задаётся только
     *                   у финального сообщения передачи, у остальных пусто
     * @param rowCount   строк в этом сообщении, у финального — ожидаемое их число
     *                   во всей передаче
     */
    public void markBuffered(UUID correlationId, @Nullable Integer batchCount, int rowCount) {
        this.status = InboundStatus.BUFFERED;
        this.correlationId = correlationId;
        this.batchCount = batchCount;
        this.rowCount = rowCount;
        this.nextRetryAt = null;
    }

    /**
     * Забыть тело сообщения, сохранив строку журнала.
     *
     * <p>Нужно каналу с персональными данными: полис приезжает полным пакетом JSON,
     * и хранить его вечно — значит держать вторую копию реестра застрахованных
     * в таблице, которая для этого не предназначена. Строка остаётся: по ней
     * по-прежнему видно, что сообщение приходило, когда и чем закончилось.
     *
     * <p>Повторить такую строку нечем — это осознанная цена, и разбор её учитывает:
     * пустое тело означает терминальный отказ с внятной причиной, а не тихий пропуск.
     */
    public void forgetPayload() {
        this.payload = null;
    }

    /** Разложено по таблицам. */
    public void markProcessed(Instant processedAt) {
        this.status = InboundStatus.PROCESSED;
        this.processedAt = processedAt;
        this.nextRetryAt = null;
        this.lastError = null;
    }

    /**
     * Не прошло фильтр потока. Причина обязательна: без неё «справочник пуст, потому что
     * данных нет» и «справочник пуст, потому что GUID вида деятельности задан неверно»
     * выглядят в базе одинаково.
     */
    public void markSkipped(String reason, Instant processedAt) {
        this.status = InboundStatus.SKIPPED;
        this.lastError = reason;
        this.processedAt = processedAt;
        this.nextRetryAt = null;
    }

    /** Временная ошибка: попытка засчитана, следующая назначена по лестнице задержек. */
    public void markRetrying(String error, Instant nextRetryAt) {
        this.status = InboundStatus.RETRY;
        this.attempt += 1;
        this.lastError = error;
        this.nextRetryAt = nextRetryAt;
    }

    /** Постоянная ошибка либо исчерпанная лестница: повторов больше не будет. */
    public void markFailed(String error) {
        this.status = InboundStatus.FAILED;
        this.lastError = error;
        this.nextRetryAt = null;
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

    @Nullable
    public String payload() {
        return payload;
    }

    public InboundStatus status() {
        return status;
    }

    public int attempt() {
        return attempt;
    }

    @Nullable
    public Instant nextRetryAt() {
        return nextRetryAt;
    }

    @Nullable
    public String lastError() {
        return lastError;
    }

    @Nullable
    public UUID correlationId() {
        return correlationId;
    }

    @Nullable
    public Integer batchCount() {
        return batchCount;
    }

    @Nullable
    public Integer rowCount() {
        return rowCount;
    }

    public Instant receivedAt() {
        return receivedAt;
    }

    @Nullable
    public Instant processedAt() {
        return processedAt;
    }
}
