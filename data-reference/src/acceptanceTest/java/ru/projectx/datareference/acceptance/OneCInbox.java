package ru.projectx.datareference.acceptance;

import io.cucumber.spring.ScenarioScope;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

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
    private static final String MESSAGE_IDS = "select message_id from inbound_message order by received_at";
    private static final String STORED = "select exists(select 1 from inbound_message where message_id = :messageId)";
    private static final String ROW = """
            select status, last_error, cast(payload as text) as payload
            from inbound_message where message_id = :messageId
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
        return jdbc.sql(ROW)
                .param("messageId", received.getLast().messageId())
                .query((row, number) -> new InboundRow(
                        row.getString("status"), row.getString("last_error"), row.getString("payload")))
                .single();
    }

    /** Конверт, присланный последним шагом «1С:УСК прислала …», в том виде, в каком его сохранил бы приём. */
    public String lastEnvelope() {
        return received.getLast().envelope();
    }

    /** Статусы, которыми разбор обязан закончить присланные сообщения, в порядке приёма. */
    public List<String> expectedStatuses() {
        return List.copyOf(expectedStatuses);
    }

    /** Статусы всех строк сырья в порядке приёма. */
    public List<String> statuses() {
        return jdbc.sql(STATUSES).query(String.class).list();
    }
}
