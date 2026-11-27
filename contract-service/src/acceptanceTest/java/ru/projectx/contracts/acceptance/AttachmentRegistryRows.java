package ru.projectx.contracts.acceptance;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class AttachmentRegistryRows {

    private final JdbcClient jdbc;

    AttachmentRegistryRows(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Entry> lookup(String identifier) {
        return jdbc.sql("""
                select id, attachment_id, doc_type::text, deleted_at, deleted_request_id
                  from contract_documents where :id in (id, attachment_id)
                """).param("id", UUID.fromString(identifier)).query(this::entry).optional();
    }

    private Entry entry(ResultSet row, int number) throws SQLException {
        return new Entry(row.getObject("id", UUID.class), row.getObject("attachment_id", UUID.class),
                row.getString("doc_type"), row.getObject("deleted_at", OffsetDateTime.class),
                row.getString("deleted_request_id"));
    }

    record Entry(UUID documentId, @Nullable UUID attachmentId, String type, @Nullable OffsetDateTime deletedAt,
            @Nullable String deletedRequestId) {
    }
}
