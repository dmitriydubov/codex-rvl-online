package ru.projectx.contracts.acceptance;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class OutboxRecords {

    private static final String COLUMNS = """
            select message_id, correlation_id, event_type, entity_version, payload::text as payload, status::text as status,
                   attempts, error_message
              from outbox_messages
            """;

    private final JdbcClient jdbc;

    public OutboxRecords(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Entry(UUID messageId, UUID correlationId, String eventType, int entityVersion, @Nullable String payload,
            String status, int attempts, @Nullable String errorMessage) {

        String outcome() {
            return switch (status) {
                case "NEW" -> attempts == 0 ? "ждёт публикации" : "отложено до повтора";
                case "PUBLISHED" -> "опубликовано";
                case "FAILED" -> "отклонено окончательно";
                default -> throw new IllegalStateException("неизвестный статус журнала исходящих: " + status);
            };
        }
    }

    List<Entry> ofRequest(String requestId) {
        return jdbc.sql(COLUMNS + " where correlation_id = :request order by created_at, id")
                .param("request", UUID.fromString(requestId)).query(this::entry).list();
    }

    Entry number(int ordinal, String requestId) {
        List<Entry> entries = ofRequest(requestId);
        if (ordinal < 1 || ordinal > entries.size()) {
            throw new AssertionError("в журнале исходящих по заявке " + requestId + " " + entries.size()
                    + " записей, записи №" + ordinal + " нет");
        }
        return entries.get(ordinal - 1);
    }

    List<Entry> all() {
        return jdbc.sql(COLUMNS + " order by created_at, id").query(this::entry).list();
    }

    private Entry entry(ResultSet row, int index) throws SQLException {
        return new Entry(row.getObject("message_id", UUID.class), row.getObject("correlation_id", UUID.class),
                row.getString("event_type"), row.getInt("entity_version"), row.getString("payload"),
                row.getString("status"), row.getInt("attempts"), row.getString("error_message"));
    }
}
