package ru.projectx.contracts.acceptance;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class QuarantineRecords {

    private static final String COLUMNS = """
            select message_id, source_queue, body, reason, received_at
              from inbox_quarantine
            """;

    private final JdbcClient jdbc;

    public QuarantineRecords(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Entry(UUID messageId, String sourceQueue, byte @Nullable [] body, String reason, Instant receivedAt) {
    }

    Optional<Entry> find(String messageId) {
        return jdbc.sql(COLUMNS + " where message_id = :id").param("id", UUID.fromString(messageId))
                .query(this::entry).optional();
    }

    long count() {
        return jdbc.sql("select count(*) from inbox_quarantine").query(Long.class).single();
    }

    private Entry entry(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        return new Entry(row.getObject("message_id", UUID.class), row.getString("source_queue"), row.getBytes("body"),
                row.getString("reason"), row.getObject("received_at", java.time.OffsetDateTime.class).toInstant());
    }
}
