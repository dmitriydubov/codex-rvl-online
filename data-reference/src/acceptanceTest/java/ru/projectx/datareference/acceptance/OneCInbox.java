package ru.projectx.datareference.acceptance;

import io.cucumber.spring.ScenarioScope;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Сырьё 1С:УСК в сценарии: строки {@code inbound_message} со статусом {@code NEW}, какие оставил бы
 * приём из очереди.
 *
 * <p>Дальше их разбирает штатный планировщик, поэтому справочники получаются тем же путём, что из
 * 1С, а не записью прямо в их таблицы. Разбор идёт по возрастанию {@code received_at}, и сообщение
 * получает момент приёма по порядку шагов «Дано»: родитель, присланный раньше, раньше и разбирается.
 * Сообщения, опубликованные в брокер приёмки ({@link OneCBroker}), кладёт в сырьё сам приём; здесь они только
 * учитываются, чтобы шаг разбора знал, каких исходов ждать.
 */
@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
public class OneCInbox {

    private static final String RECEIVE = """
            insert into inbound_message (message_id, stream, source_queue, payload, status, attempt, received_at)
            values (:messageId, :stream, :queue, cast(:payload as jsonb), 'NEW', 0,
                    now() + make_interval(secs => :order))
            """;
    private static final String STATUSES = "select status from inbound_message order by received_at";
    private static final String TRANSFER_STATUSES = """
            select status from inbound_message where payload -> 'data' ->> 'cid' = :cid order by received_at
            """;
    private static final String MESSAGE_IDS = "select message_id from inbound_message order by received_at";
    private static final String STORED = "select exists(select 1 from inbound_message where message_id = :messageId)";
    private static final String ROW = """
            select status, attempt, last_error, cast(payload as text) as payload
            from inbound_message where message_id = :messageId
            """;
    private static final String RETRY_DUE = """
            update inbound_message set next_retry_at = now() - interval '1 second' where status = 'RETRY'
            """;
    private static final String RETURN_TO_PARSING = """
            update inbound_message set status = 'NEW', next_retry_at = null, last_error = null, processed_at = null
            where message_id = :messageId
            """;
    private static final String RETURN_TRANSFER_TO_PARSING = """
            update inbound_message set status = 'NEW', next_retry_at = null, last_error = null, processed_at = null
            where correlation_id = :cid
            """;
    private static final String AGE = """
            update inbound_message set received_at = now() - cast(:age as interval) where message_id = :messageId
            """;

    private static final String AGE_ALL = """
            update inbound_message set received_at = received_at - cast(:age as interval)
            """;
    private static final String EXPIRED_PARENTS = """
            select awaited_parent_id from inbound_message
            where stream = :stream and status = 'FAILED' and parent_wait_since is not null
            order by received_at
            """;
    private static final String AGE_PARENT_WAITS = """
            update inbound_message set parent_wait_since = parent_wait_since - cast(:age as interval)
            where status = 'AWAITING_PARENT'
            """;

    private final JdbcClient jdbc;
    private final List<String> expectedStatuses = new ArrayList<>();
    private final List<OneCMessage> received = new ArrayList<>();

    public OneCInbox(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void receive(OneCMessage message) {
        expect(message);
        jdbc.sql(RECEIVE)
                .param("messageId", message.messageId())
                .param("stream", message.stream().name())
                .param("queue", message.queue())
                .param("payload", message.envelope())
                .param("order", expectedStatuses.size())
                .update();
    }

    /**
     * Сообщение, которое в сырьё положит сам приём из брокера: шаг только запоминает, какой исход разбора от
     * него ждать. Повтор того же {@code message-id} — дубль, он строки сырья не добавляет и здесь не считается.
     */
    public void expect(OneCMessage message) {
        if (received.stream().noneMatch(known -> known.messageId().equals(message.messageId()))) {
            expectedStatuses.add(message.expected().name());
        }
        received.add(message);
    }

    /** {@code message-id} всех строк сырья в порядке приёма. */
    public List<UUID> messageIdsInOrder() {
        return jdbc.sql(MESSAGE_IDS).query(UUID.class).list();
    }

    /** Есть ли строка сырья у сообщения. */
    public boolean stored(UUID messageId) {
        return jdbc.sql(STORED).param("messageId", messageId).query(Boolean.class).single();
    }

    /** Строка сырья сообщения, присланного последним шагом «1С:УСК прислала …». */
    public InboundRow last() {
        return row(received.getLast().messageId());
    }

    /** Строка сырья сообщения с этим {@code message-id}. */
    public InboundRow row(UUID messageId) {
        return jdbc.sql(ROW)
                .param("messageId", messageId)
                .query((row, number) -> new InboundRow(row.getString("status"), row.getInt("attempt"),
                        row.getString("last_error"), row.getString("payload")))
                .single();
    }

    /** Строка сырья сообщения, присланного первым шагом «1С:УСК прислала …». */
    public InboundRow first() {
        return row(received.getFirst().messageId());
    }

    /**
     * Срок повтора наступил у всех сообщений, отложенных до повтора: часы стенда не остановлены, а ждать лестницу
     * повторов нельзя, поэтому срок переносится в прошлое.
     */
    public void retryDueNow() {
        if (jdbc.sql(RETRY_DUE).update() == 0) {
            throw new IllegalStateException("в сырье нет сообщений, отложенных до повтора");
        }
    }

    /**
     * Последнее сообщение снова ждёт разбора, как после ручной переобработки дежурным: тело и момент приёма прежние,
     * исход стёрт.
     */
    public void returnLastToParsing() {
        int returned = jdbc.sql(RETURN_TO_PARSING).param("messageId", received.getLast().messageId()).update();
        if (returned != 1) {
            throw new IllegalStateException("в сырье нет строки последнего сообщения");
        }
    }

    /**
     * Сообщение, которое 1С:УСК присылает ещё раз с тем же содержимым, — новая выгрузка со своим {@code message-id}, а
     * не дубль доставки. Первое такое сообщение остаётся как есть; у каждого следующего {@code message-id} выводится из
     * прежнего и номера присылки, так что повторный прогон сценария кладёт те же строки.
     */
    public OneCMessage asNewDelivery(OneCMessage message) {
        long sent = received.stream().filter(known -> known.messageId().equals(message.messageId())).count();
        if (sent == 0) {
            return message;
        }
        UUID resent = UUID.nameUUIDFromBytes((message.messageId() + "#" + sent).getBytes(StandardCharsets.UTF_8));
        return new OneCMessage(message.stream(), message.queue(), message.data(), message.expected(), resent);
    }

    /** Все присланные сообщения потока в порядке присылки. */
    public List<OneCMessage> receivedOf(OneCStream stream) {
        return received.stream().filter(message -> message.stream() == stream).toList();
    }

    /**
     * Все части передачи прайса снова ждут разбора, как после ручной переобработки передачи целиком: тела и моменты
     * приёма прежние, исход стёрт.
     */
    public void returnTransferToParsing(UUID cid) {
        if (jdbc.sql(RETURN_TRANSFER_TO_PARSING).param("cid", cid).update() == 0) {
            throw new IllegalStateException("в сырье нет частей передачи " + cid);
        }
    }

    /** Сообщение, присланное последним шагом «1С:УСК прислала …». */
    public OneCMessage lastMessage() {
        return received.getLast();
    }

    /** Последнее присланное сообщение потока: следующее сообщение сценария ссылается на него. */
    public OneCMessage lastOf(OneCStream stream) {
        return received.reversed().stream()
                .filter(message -> message.stream() == stream)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("сценарий не присылал сообщений потока " + stream));
    }

    /**
     * Отодвигает момент приёма последнего сообщения в прошлое на {@code age}: часы стенда не остановлены, а ждать
     * срок хранения нельзя.
     */
    public void ageLast(Duration age) {
        int aged = jdbc.sql(AGE)
                .param("age", age.toSeconds() + " seconds")
                .param("messageId", received.getLast().messageId())
                .update();
        if (aged != 1) {
            throw new IllegalStateException("в сырье нет строки последнего сообщения");
        }
    }

    /**
     * Отодвигает момент приёма всех сообщений сырья в прошлое на одну и ту же величину {@code age}: порядок приёма и
     * промежутки между сообщениями сохраняются, а сообщения, присланные позже, примутся «сейчас».
     */
    public void ageAll(Duration age) {
        if (jdbc.sql(AGE_ALL).param("age", age.toSeconds() + " seconds").update() == 0) {
            throw new IllegalStateException("в сырье нет сообщений");
        }
    }

    /**
     * Отодвигает момент начала ожидания у всех сообщений, которые ждут родителя, в прошлое на {@code age}: ждать
     * срок ожидания сценарий не может. Момент приёма не меняется.
     */
    public void ageParentWaits(Duration age) {
        if (jdbc.sql(AGE_PARENT_WAITS).param("age", age.toSeconds() + " seconds").update() == 0) {
            throw new IllegalStateException("в сырье нет сообщений, которые ждут родителя");
        }
    }

    /** Родители, которых не дождались отклонённые по сроку сообщения потока, без повторов, в порядке приёма. */
    public List<UUID> expiredParentsInOrder(OneCStream stream) {
        return jdbc.sql(EXPIRED_PARENTS).param("stream", stream.name()).query(UUID.class).list().stream()
                .distinct()
                .toList();
    }

    /** Конверт, присланный последним шагом «1С:УСК прислала …», в том виде, в каком его сохранил бы приём. */
    public String lastEnvelope() {
        return received.getLast().envelope();
    }

    /** Статусы, которыми разбор обязан закончить присланные сообщения, в порядке приёма. */
    public List<String> expectedStatuses() {
        return List.copyOf(expectedStatuses);
    }

    /**
     * Статусы частей передачи прайса в порядке приёма. Часть узнаётся по идентификатору передачи в теле: колонку
     * передачи сервис заполняет не у каждой части.
     */
    public List<String> transferStatuses(UUID cid) {
        return jdbc.sql(TRANSFER_STATUSES).param("cid", cid.toString()).query(String.class).list();
    }

    /** Статусы всех строк сырья в порядке приёма. */
    public List<String> statuses() {
        return jdbc.sql(STATUSES).query(String.class).list();
    }
}
