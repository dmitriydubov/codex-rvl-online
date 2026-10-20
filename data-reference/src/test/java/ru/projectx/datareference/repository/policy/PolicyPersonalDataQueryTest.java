package ru.projectx.datareference.repository.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionOperations;
import ru.projectx.datareference.entity.policy.Policy;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class PolicyPersonalDataQueryTest {

    private static final LocalDate ENDED_BEFORE = LocalDate.of(2026, 2, 12);
    private static final Instant ARCHIVED_BY = Instant.parse("2026-02-12T09:15:00Z");
    private static final Instant ERASED_AT = Instant.parse("2026-03-14T09:15:00Z");
    private static final LocalDate FAR_END = LocalDate.of(2030, 12, 31);
    private static final long WAIT_SECONDS = 10;
    private static final String RETURNED_TO_ACTION =
            "update policies set is_archive = false, archived_at = null where external_id = :externalId";
    private static final String ERASURE_WAITS_FOR_LOCK = "select count(*) from pg_stat_activity "
            + "where wait_event_type = 'Lock' and query like 'update policies%personal_data_erased_at = %'";

    @Autowired
    private PolicyRepository policies;

    @Autowired
    private TransactionOperations transactions;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    @Test
    void endedBeforeTheDayOrArchivedByTheMoment_areErased_theRestKeep() {
        UUID endedEarlier = store(LocalDate.of(2026, 2, 11), false, ARCHIVED_BY);
        UUID endedOnTheDay = store(ENDED_BEFORE, false, ARCHIVED_BY);
        UUID archivedOnTheMoment = store(FAR_END, true, ARCHIVED_BY);
        UUID archivedLater = store(FAR_END, true, ARCHIVED_BY.plusSeconds(1));
        UUID openEnded = store(null, false, ARCHIVED_BY);

        assertThat(policies.countPersonalDataBeyond(ENDED_BEFORE, ARCHIVED_BY)).isEqualTo(2);
        assertThat(erase(10)).isEqualTo(2);

        assertThat(List.of(endedEarlier, archivedOnTheMoment)).allSatisfy(this::assertErased);
        assertThat(List.of(endedOnTheDay, archivedLater, openEnded)).allSatisfy(this::assertKept);
        assertThat(policies.countPersonalDataBeyond(ENDED_BEFORE, ARCHIVED_BY)).isZero();
    }

    @Test
    void batch_erasesNoMoreThanItsLimit_andErasedPoliciesAreNotTouchedAgain() {
        store(LocalDate.of(2026, 1, 31), false, ARCHIVED_BY);
        store(LocalDate.of(2026, 1, 30), false, ARCHIVED_BY);

        assertThat(erase(1)).isEqualTo(1);
        assertThat(erase(1)).isEqualTo(1);
        assertThat(erase(1)).isZero();
    }

    @Test
    void policyReturnedToActionWhileErasureWaits_keepsItsPersonalData() throws Exception {
        UUID archived = store(FAR_END, true, ARCHIVED_BY.minusSeconds(60));
        CountDownLatch rowLocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<?> parsing = threads.submit(() -> transactions.executeWithoutResult(status -> {
                jdbc.sql(RETURNED_TO_ACTION).param("externalId", archived).update();
                rowLocked.countDown();
                awaitQuietly(release);
            }));
            assertThat(rowLocked.await(WAIT_SECONDS, TimeUnit.SECONDS)).as("разбор взял строку").isTrue();
            Future<Integer> erasure = threads.submit(() -> erase(10));
            await().atMost(Duration.ofSeconds(WAIT_SECONDS))
                    .until(() -> jdbc.sql(ERASURE_WAITS_FOR_LOCK).query(Long.class).single() > 0);
            release.countDown();
            parsing.get(WAIT_SECONDS, TimeUnit.SECONDS);

            assertThat(erasure.get(WAIT_SECONDS, TimeUnit.SECONDS)).isZero();
        }
        finally {
            release.countDown();
            threads.shutdownNow();
        }
        assertKept(archived);
    }

    private static void awaitQuietly(CountDownLatch release) {
        try {
            release.await(WAIT_SECONDS, TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void assertErased(UUID externalId) {
        Policy policy = policies.findByExternalId(externalId).orElseThrow();
        assertThat(policy.personalDataErased()).isTrue();
        assertThat(policy.details().lastName()).isNull();
        assertThat(policy.details().firstName()).isNull();
        assertThat(policy.details().middleName()).isNull();
        assertThat(policy.details().dateOfBirth()).isNull();
        assertThat(policy.details().email()).isNull();
        assertThat(policy.details().number()).isEqualTo("П-0001");
        assertThat(policy.details().gender()).isEqualTo("Мужской");
    }

    private void assertKept(UUID externalId) {
        Policy policy = policies.findByExternalId(externalId).orElseThrow();
        assertThat(policy.personalDataErased()).isFalse();
        assertThat(policy.details().lastName()).isEqualTo("Тестов");
        assertThat(policy.details().email()).isEqualTo("test@example.org");
    }

    private int erase(int limit) {
        Integer erased = transactions.execute(
                status -> policies.erasePersonalDataBeyond(ENDED_BEFORE, ARCHIVED_BY, ERASED_AT, limit));
        return erased == null ? 0 : erased;
    }

    private UUID store(@Nullable LocalDate endDate, boolean archived, Instant receivedAt) {
        UUID externalId = UUID.randomUUID();
        var details = new Policy.Details(UUID.randomUUID(), "П-0001", "Тестов", "Тест", "Тестович",
                LocalDate.of(1990, 1, 1), "Мужской", "test@example.org", LocalDate.of(2026, 1, 1), endDate, true,
                archived);
        policies.save(Policy.create(externalId, new Policy.Snapshot(details, List.of(), List.of()), receivedAt));
        return externalId;
    }
}
