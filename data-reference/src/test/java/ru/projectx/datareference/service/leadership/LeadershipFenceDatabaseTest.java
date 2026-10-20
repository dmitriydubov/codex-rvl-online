package ru.projectx.datareference.service.leadership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.datareference.repository.database.LeaderLockRepository;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class LeadershipFenceDatabaseTest {

    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final String OTHER_INSTANCE = "5c1d9e2f-3a4b-4c5d-8e6f-7a8b9c0d1e2f";
    private static final String TAKE_OVER_EXPIRED = """
            update leader_lock
               set client_id = :other, expired_after = :expires
             where region = :group and lock_key = :key and (client_id = :other or expired_after < :now)
            """;

    @Autowired
    private LeaderLockRepository locks;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private LeadershipFence contextFence;

    private final LeaderLock lock = LeaderLock.forNewInstance("fence-" + UUID.randomUUID(), "onec-background");

    private LeadershipFence fence;
    private JdbcClient jdbc;

    @BeforeEach
    void thisInstanceHoldsAnExpiredLock() {
        fence = new LeadershipFence(locks, lock);
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                insert into leader_lock (region, lock_key, client_id, created_date, expired_after)
                values (:group, :key, :instance, :now, :now)
                """)
                .param("group", lock.group())
                .param("key", lock.key())
                .param("instance", lock.instanceId())
                .param("now", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                .update();
    }

    @AfterEach
    void releaseLock() {
        jdbc.sql("delete from leader_lock where region = :group").param("group", lock.group()).update();
    }

    @Test
    void ownLockRow_isHeldEvenAfterItsTermByThisInstanceClock() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> fence.holdUntilCommit());
    }

    @Test
    void lockRowTakenByAnotherInstance_failsTheNextTransaction() {
        takeOver();

        assertThatThrownBy(() -> new TransactionTemplate(transactions)
                .executeWithoutResult(status -> fence.holdUntilCommit()))
                .isInstanceOf(LeadershipLostException.class);
    }

    @Test
    void takeOverOfAnExpiredLock_waitsForTheTransactionHoldingItAndThenWins() throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CompletableFuture<Void> transaction = CompletableFuture.runAsync(() -> new TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                    fence.holdUntilCommit();
                    held.countDown();
                    awaitQuietly(commit);
                }));
        assertThat(held.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Integer> takeOver = CompletableFuture.supplyAsync(this::takeOver);

        await().during(Duration.ofMillis(500)).atMost(WAIT).until(() -> !takeOver.isDone());
        commit.countDown();
        transaction.get(WAIT.toSeconds(), TimeUnit.SECONDS);
        assertThat(takeOver.get(WAIT.toSeconds(), TimeUnit.SECONDS)).isEqualTo(1);
        assertThatThrownBy(() -> new TransactionTemplate(transactions)
                .executeWithoutResult(status -> fence.holdUntilCommit()))
                .isInstanceOf(LeadershipLostException.class);
    }

    @Test
    void fenceOutsideATransaction_isRefused() {
        assertThatThrownBy(contextFence::holdUntilCommit).isInstanceOf(IllegalTransactionStateException.class);
    }

    private int takeOver() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return jdbc.sql(TAKE_OVER_EXPIRED)
                .param("other", OTHER_INSTANCE)
                .param("expires", now.plusMinutes(1))
                .param("group", lock.group())
                .param("key", lock.key())
                .param("now", now)
                .update();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(WAIT.toSeconds(), TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
