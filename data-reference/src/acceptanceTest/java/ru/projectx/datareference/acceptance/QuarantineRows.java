package ru.projectx.datareference.acceptance;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class QuarantineRows {

    private static final String ROWS = """
            select message_id, source_queue, reason, body
              from quarantine where message_id = :messageId
            """;
    private static final String COUNT = "select count(*) from quarantine";
    private static final String CONTAINS = "select exists(select 1 from quarantine where message_id = :messageId)";
    private static final String AGE = """
            update quarantine set received_at = cast(:now as timestamptz) - cast(:age as interval)
             where message_id = :messageId
            """;

    private final JdbcClient jdbc;
    private final StandClock clock;

    public QuarantineRows(JdbcClient jdbc, StandClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public List<Row> rowsOf(UUID messageId) {
        return jdbc.sql(ROWS)
                .param("messageId", messageId)
                .query((row, number) -> new Row(
                        row.getString("source_queue"), row.getString("reason"), row.getBytes("body")))
                .list();
    }

    public boolean contains(UUID messageId) {
        return jdbc.sql(CONTAINS).param("messageId", messageId).query(Boolean.class).single();
    }

    public long count() {
        return jdbc.sql(COUNT).query(Long.class).single();
    }

    public void age(UUID messageId, Duration age) {
        int updated = jdbc.sql(AGE)
                .param("now", clock.moment())
                .param("age", age.toSeconds() + " seconds")
                .param("messageId", messageId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("в карантине нет записи " + messageId);
        }
    }

    public record Row(String sourceQueue, String reason, @Nullable byte[] body) {
    }
}
