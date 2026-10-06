package ru.projectx.datareference.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionOperations;
import org.yaml.snakeyaml.Yaml;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

/**
 * Таблица полисов после введения срока хранения персональных данных. Полисам, которые лежали в архиве до обновления,
 * миграция ставит момент перевода в архив по времени последнего применённого сообщения, а у остальных он пуст. Две
 * проверки в базе держат прежние гарантии: без стёртых данных фамилия, имя и дата рождения обязательны, а момент
 * перевода в архив есть ровно у архивного полиса.
 *
 * <p>Заполнение проверяется ровно тем текстом, что лежит в миграции, на временной копии таблицы без проверок: в
 * настоящей таблице архивный полис без момента перевода уже не сохранить.
 */
@IntegrationTest
class PolicyPersonalDataStorageTest {

    private static final Instant UPDATED = Instant.parse("2026-02-01T10:00:00Z");
    private static final String COPY = "create temporary table policies (like public.policies) on commit drop";
    private static final String INSERT = """
            insert into policies (id, external_id, external_contract_id, number, last_name, first_name, date_of_birth,
                                  begin_date, availability, is_archive, created_at, updated_at, archived_at,
                                  personal_data_erased_at)
            values (:id, :id, :id, 'П-0001', :lastName, 'Тест', date '1990-01-01', date '2026-01-01', true,
                    :archive, :updated, :updated, :archivedAt, :erasedAt)
            """;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionOperations transactions;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    @Test
    void fill_givesArchivedPoliciesTheirLastUpdate_andLeavesTheRestEmpty() {
        List<Timestamp> archivedAt = transactions.execute(status -> {
            jdbc.sql(COPY).update();
            UUID archived = insert("Тестов", true, null, null);
            UUID active = insert("Тестов", false, null, null);
            fillSql().forEach(sql -> jdbc.sql(sql).update());
            fillSql().forEach(sql -> jdbc.sql(sql).update());
            return List.of(archived, active).stream()
                    .map(id -> jdbc.sql("select archived_at from policies where id = :id").param("id", id)
                            .query(Timestamp.class).optional().orElse(null))
                    .toList();
        });

        assertThat(archivedAt).containsExactly(Timestamp.from(UPDATED), null);
    }

    @Test
    void policyWithoutLastName_isRejected_untilItsPersonalDataAreErased() {
        assertThatThrownBy(() -> insert(null, false, null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_policies_personal_data");

        insert(null, false, null, UPDATED);
    }

    @Test
    void archiveAndMomentOfArchiving_goTogether() {
        assertThatThrownBy(() -> insert("Тестов", true, null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_policies_archived_at");
        assertThatThrownBy(() -> insert("Тестов", false, UPDATED, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_policies_archived_at");

        insert("Тестов", true, UPDATED, null);
    }

    private UUID insert(@Nullable String lastName, boolean archive, @Nullable Instant archivedAt,
            @Nullable Instant erasedAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql(INSERT)
                .param("id", id)
                .param("lastName", lastName)
                .param("archive", archive)
                .param("updated", Timestamp.from(UPDATED))
                .param("archivedAt", archivedAt == null ? null : Timestamp.from(archivedAt))
                .param("erasedAt", erasedAt == null ? null : Timestamp.from(erasedAt))
                .update();
        return id;
    }

    /** Операторы заполнения ровно тем текстом, что лежит в миграции. */
    @SuppressWarnings("unchecked")
    private static List<String> fillSql() {
        try (InputStream changelog = new ClassPathResource("db/changelog/019-policy-personal-data-retention.yaml")
                .getInputStream()) {
            Map<String, Object> root = new Yaml().load(changelog);
            return ((List<Map<String, Object>>) root.get("databaseChangeLog")).stream()
                    .map(entry -> (Map<String, Object>) entry.get("changeSet"))
                    .filter(changeSet -> "019-policy-archived-at-fill".equals(changeSet.get("id")))
                    .flatMap(changeSet -> ((List<Map<String, Map<String, String>>>) changeSet.get("changes")).stream())
                    .map(change -> change.get("sql").get("sql"))
                    .toList();
        }
        catch (IOException unreadable) {
            throw new IllegalStateException("миграция заполнения не читается", unreadable);
        }
    }
}
