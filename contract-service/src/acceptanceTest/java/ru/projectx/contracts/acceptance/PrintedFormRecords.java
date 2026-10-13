package ru.projectx.contracts.acceptance;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class PrintedFormRecords {

    private static final String FORMS = """
            select id, kind::text as kind, doc_type::text as doc_type, usk_file_url, file_size
              from contract_documents
             where lm_policy_id = :policy
             order by created_at, id
            """;

    private final JdbcClient jdbc;

    public PrintedFormRecords(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Form(UUID id, String kind, String docType, @Nullable String url, @Nullable Long size) {
    }

    List<Form> ofPolicy(UUID policyId) {
        return jdbc.sql(FORMS).param("policy", policyId).query(this::form).list();
    }

    private Form form(ResultSet row, int index) throws SQLException {
        long size = row.getLong("file_size");
        return new Form(row.getObject("id", UUID.class), row.getString("kind"), row.getString("doc_type"),
                row.getString("usk_file_url"), row.wasNull() ? null : size);
    }
}
