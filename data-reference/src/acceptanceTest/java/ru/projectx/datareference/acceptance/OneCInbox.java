package ru.projectx.datareference.acceptance;

import io.cucumber.spring.ScenarioScope;
import java.util.List;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Сырьё 1С:УСК сценария: строки {@code inbound_message} со статусом {@code NEW} — то, что
 * оставил бы приём из брокера. Дальше их разбирает штатный планировщик (решение Р6):
 * справочники получаются тем же путём, что из 1С, а не записью в их таблицы.
 *
 * <p>Разбор идёт по возрастанию {@code received_at}, поэтому сообщение получает момент
 * по порядку шагов «Дано»: родитель, присланный раньше, раньше и разбирается.
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

    private final JdbcClient jdbc;
    private int received;

    public OneCInbox(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void receive(OneCMessage message) {
        received++;
        jdbc.sql(RECEIVE)
                .param("messageId", message.messageId())
                .param("stream", message.stream().name())
                .param("queue", message.queue())
                .param("payload", message.envelope())
                .param("order", received)
                .update();
    }

    /** Сколько сообщений сценарий положил в сырьё. */
    public int received() {
        return received;
    }

    /** Статусы всех строк сырья в порядке приёма. */
    public List<String> statuses() {
        return jdbc.sql(STATUSES).query(String.class).list();
    }
}
