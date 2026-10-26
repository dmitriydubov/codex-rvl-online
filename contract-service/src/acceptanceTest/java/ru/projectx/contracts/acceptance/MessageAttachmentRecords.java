package ru.projectx.contracts.acceptance;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class MessageAttachmentRecords {

    private static final String ATTACHMENTS = """
            select a.document_id, a.file_key, a.content_type, a.file_size
              from message_attachments a
              join outbox_messages m on m.id = a.outbox_message_id
             where m.message_id = :message
             order by a.created_at, a.id
            """;

    private final JdbcClient jdbc;

    public MessageAttachmentRecords(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Attachment(UUID documentId, String fileKey, String contentType, long fileSize) {
    }

    List<Attachment> of(UUID messageId) {
        return jdbc.sql(ATTACHMENTS).param("message", messageId).query(this::attachment).list();
    }

    private Attachment attachment(ResultSet row, int index) throws SQLException {
        return new Attachment(row.getObject("document_id", UUID.class), row.getString("file_key"),
                row.getString("content_type"), row.getLong("file_size"));
    }
}
