package ru.projectx.contracts.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

final class RowWaits {

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final String LOCK_WAITS = "select count(*) from pg_stat_activity"
            + " where wait_event_type = 'Lock' and datname = current_database()"
            + " and application_name <> 'contract-service-leader-lock'";

    private final TransactionTemplate transaction;
    private final JdbcClient jdbc;

    RowWaits(TransactionTemplate transaction, JdbcClient jdbc) {
        this.transaction = transaction;
        this.jdbc = jdbc;
    }

    void commitTheFirstOnceTheSecondWaitsForItsRows(Runnable first, Runnable second)
            throws InterruptedException, ExecutionException, TimeoutException {
        CountDownLatch applied = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(status -> {
            first.run();
            applied.countDown();
            awaitQuietly(commit);
        }));
        try {
            await().atMost(WAIT).until(() -> applied.getCount() == 0 || holder.isDone());
            holder.getNow(null);
            assertThat(holder).as("первый путь применён и ждёт фиксации").isNotDone();
            CompletableFuture<Void> waiter = CompletableFuture.runAsync(second);
            await().atMost(WAIT).until(() -> waiter.isDone() || somebodyWaitsForALock());
            waiter.getNow(null);
            assertThat(waiter).as("второй путь ждёт строки первого").isNotDone();
            commit.countDown();
            holder.get(WAIT.toSeconds(), TimeUnit.SECONDS);
            waiter.get(WAIT.toSeconds(), TimeUnit.SECONDS);
        }
        finally {
            commit.countDown();
        }
    }

    private boolean somebodyWaitsForALock() {
        return jdbc.sql(LOCK_WAITS).query(Long.class).single() > 0;
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
