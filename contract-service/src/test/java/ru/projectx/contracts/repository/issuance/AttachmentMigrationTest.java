package ru.projectx.contracts.repository.issuance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import ru.projectx.contracts.support.IntegrationTest;

@IntegrationTest
class AttachmentMigrationTest {

    private static final String SCHEMA = "attachment_migration_test";
    private static final String MASTER = "db/changelog/db.changelog-master.yaml";
    private static final String KNOWN = "61a7982c-a0b4-4bbf-9527-60b40b752803";
    private static final String EARLY = "0f4e2a9c-6b18-4c73-a5d0-9e2b7f1c3a66";
    private static final String PERSON = "9275471c-d2fe-484f-97a6-d6d7b1267b5e";

    @Autowired
    private SpringLiquibase applicationMigration;

    @Test
    void rollbackRestoresThePriorSchemaAndCanBeAppliedAgain() throws Exception {
        schema("create schema " + SCHEMA);
        try (Connection connection = applicationMigration.getDataSource().getConnection();
             Liquibase migration = migration(connection)) {
            migration.update();
            JdbcClient jdbc = JdbcClient.create(new SingleConnectionDataSource(connection, true));
            jdbc.sql("""
                    insert into issuance_requests (individual_request_id, snapshot_version, updated_at)
                    values (cast(:id as uuid), 0, now())
                    """).param("id", KNOWN).update();
            certificate(jdbc, KNOWN, "retained");
            certificate(jdbc, EARLY, "early");
            certificate(jdbc, KNOWN, "deleted");
            jdbc.sql("""
                    update contract_documents set deleted_at = now(), delete_request_id = 'stand-delete'
                    where file_key = 'deleted'
                    """).update();
            consent(jdbc);
            connection.commit();

            migration.rollback(2, new Contexts(), new LabelExpression());

            assertPriorSchema(jdbc);
            assertThatThrownBy(() -> certificate(jdbc, EARLY, "refused"))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_contract_documents_request");
            connection.rollback();

            migration.update();
            certificate(jdbc, EARLY, "early-again");
            consent(jdbc);
            assertThat(keys(jdbc)).containsExactly("consent", "early-again", "retained");
        } finally {
            schema("drop schema " + SCHEMA + " cascade");
        }
    }

    private static void assertPriorSchema(JdbcClient jdbc) {
        assertThat(keys(jdbc)).containsExactly("retained");
        assertThat(jdbc.sql("""
                select enumlabel from pg_enum join pg_type on enumtypid = pg_type.oid
                where pg_type.oid = 'printed_form_type'::regtype order by enumsortorder
                """).query(String.class).list()).containsExactly("CONTRACT", "POLICY", "APPENDIX", "MEDICAL_CERTIFICATE");
        assertThat(jdbc.sql("""
                select column_name from information_schema.columns
                where table_schema = current_schema() and table_name = 'contract_documents'
                """).query(String.class).list())
                .doesNotContain("policy_id", "family_member_id", "deleted_at", "delete_request_id");
    }

    private static List<String> keys(JdbcClient jdbc) {
        return jdbc.sql("select file_key from contract_documents order by file_key").query(String.class).list();
    }

    private static void certificate(JdbcClient jdbc, String request, String key) {
        jdbc.sql("""
                insert into contract_documents (id, kind, doc_type, version, created_at, individual_request_id,
                  participant_id, attachment_id, file_key, file_hash, file_size, content_type)
                values (gen_random_uuid(), 'FINAL', 'MEDICAL_CERTIFICATE', 1, now(), cast(:request as uuid),
                  cast(:person as uuid), gen_random_uuid(), :key, 'hash', 1, 'image/png')
                """).param("request", request).param("person", PERSON).param("key", key).update();
    }

    private static void consent(JdbcClient jdbc) {
        jdbc.sql("""
                insert into contract_documents (id, kind, doc_type, version, created_at, policy_id,
                  family_member_id, attachment_id, file_key, file_hash, file_size, content_type)
                values (gen_random_uuid(), 'FINAL', 'PD_CONSENT', 1, now(), cast(:policy as uuid),
                  cast(:person as uuid), gen_random_uuid(), 'consent', 'hash', 1, 'image/png')
                """).param("policy", EARLY).param("person", PERSON).update();
    }

    private void schema(String sql) throws SQLException {
        try (Connection connection = applicationMigration.getDataSource().getConnection();
             var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static Liquibase migration(Connection connection) throws Exception {
        connection.setSchema(SCHEMA);
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        database.setDefaultSchemaName(SCHEMA);
        database.setLiquibaseSchemaName(SCHEMA);
        return new Liquibase(MASTER, new ClassLoaderResourceAccessor(), database);
    }
}
