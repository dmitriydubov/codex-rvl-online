package ru.projectx.contracts.repository.journal;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class InboxProbeRepository {

    private static final String INBOX_ROW_SHARED = """
            select 1
              from inbox_messages
             where id = :id
               for share
            """;
    private static final String QUARANTINE_ROW_SHARED = """
            select 1
              from inbox_quarantine
             where message_id = :id
               for share
            """;

    private final JdbcClient jdbc;

    public InboxProbeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean inboxRowExists(UUID id) {
        return !jdbc.sql(INBOX_ROW_SHARED).param("id", id).query(Integer.class).list().isEmpty();
    }

    public boolean quarantineRowExists(UUID messageId) {
        return !jdbc.sql(QUARANTINE_ROW_SHARED).param("id", messageId).query(Integer.class).list().isEmpty();
    }
}
