package ru.projectx.contracts.acceptance;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class InboxJournal {

    private static final String COLUMNS = """
            select message_id, correlation_id, event_type, entity_version, payload::text as payload, status::text as status,
                   attempts, error_message, next_attempt_at
              from inbox_messages
            """;

    private final JdbcClient jdbc;

    public InboxJournal(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Entry(UUID messageId, UUID correlationId, String eventType, int entityVersion, @Nullable String payload,
            String status, int attempts, @Nullable String errorMessage, @Nullable Instant nextAttemptAt) {

        String outcome() {
            return switch (status) {
                case "NEW" -> attempts == 0 ? "ждёт разбора" : "отложено до повтора";
                case "PROCESSED" -> "обработано";
                case "SKIPPED" -> "пропущено";
                case "FAILED" -> "отклонено окончательно";
                default -> throw new IllegalStateException("неизвестный статус журнала: " + status);
            };
        }
    }

    Optional<Entry> find(String messageId) {
        return jdbc.sql(COLUMNS + " where message_id = :id").param("id", UUID.fromString(messageId))
                .query(this::entry).optional();
    }

    List<Entry> inOrderOfReceipt() {
        return jdbc.sql(COLUMNS + " order by received_at, id").query(this::entry).list();
    }

    Optional<Integer> processedVersion(String requestId) {
        return jdbc.sql("select max(entity_version) from inbox_messages where correlation_id = :id and status = 'PROCESSED'")
                .param("id", UUID.fromString(requestId)).query(Integer.class).optional();
    }

    private Entry entry(java.sql.ResultSet row, int index) throws java.sql.SQLException {
        return new Entry(row.getObject("message_id", UUID.class), row.getObject("correlation_id", UUID.class),
                row.getString("event_type"), row.getInt("entity_version"), row.getString("payload"),
                row.getString("status"), row.getInt("attempts"), row.getString("error_message"),
                row.getObject("next_attempt_at", java.time.OffsetDateTime.class) == null ? null
                        : row.getObject("next_attempt_at", java.time.OffsetDateTime.class).toInstant());
    }
}
