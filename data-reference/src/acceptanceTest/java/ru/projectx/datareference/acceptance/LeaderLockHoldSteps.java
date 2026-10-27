package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Level;
import com.zaxxer.hikari.HikariDataSource;
import io.cucumber.java.After;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import io.cucumber.java.ru.Тогда;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.datareference.config.LeadershipProperties;
import ru.projectx.datareference.config.restread.RestReadDataSource;
import ru.projectx.datareference.repository.database.SessionSettingsRepository;
import ru.projectx.datareference.service.leadership.BackgroundLeadership;
import ru.projectx.datareference.service.leadership.LeadershipFence;

public class LeaderLockHoldSteps {

    private static final int AFTER_DATABASE_RESTORED_BEFORE_LOCK_RELEASE = 50;
    private static final String LOCK_TIMEOUT = "lock_timeout";
    private static final String ROLE_LOST = "больше не ведущий";
    private static final Duration MARGIN = Duration.ofSeconds(2);
    private static final Duration OPEN_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration FINISH_TIMEOUT = Duration.ofSeconds(30);

    private final LeadershipFence fence;
    private final TransactionTemplate transaction;
    private final BackgroundLeadership leadership;
    private final LeadershipProperties leadershipSettings;
    private final SessionSettingsRepository sessionSettings;
    private final DataSource dataSource;
    private final LeaderLockStand stand;
    private final ServiceJournal journal;

    private final CountDownLatch held = new CountDownLatch(1);
    private final CountDownLatch commit = new CountDownLatch(1);
    private @Nullable CompletableFuture<Void> heldTransaction;
    private @Nullable CompletableFuture<Void> takeOverAttempt;

    public LeaderLockHoldSteps(LeadershipFence fence, PlatformTransactionManager transactions,
            BackgroundLeadership leadership, LeadershipProperties leadershipSettings,
            SessionSettingsRepository sessionSettings, DataSource dataSource, LeaderLockStand stand,
            ServiceJournal journal) {

        this.fence = fence;
        this.transaction = new TransactionTemplate(transactions);
        this.leadership = leadership;
        this.leadershipSettings = leadershipSettings;
        this.sessionSettings = sessionSettings;
        this.dataSource = dataSource;
        this.stand = stand;
        this.journal = journal;
    }

    @Дано("Дата-референс держит блокировку ведущего в незавершённой транзакции разбора")
    public void serviceTransactionHoldsTheLeaderLock() throws InterruptedException {
        heldTransaction = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(status -> {
            fence.holdUntilCommit();
            held.countDown();
            awaitQuietly(commit);
        }));
        assertThat(held.await(OPEN_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                .as("транзакция разбора держит блокировку ведущего")
                .isTrue();
    }

    @Когда("незавершённая транзакция разбора держит блокировку ведущего дольше ожидания блокировки и шага продления роли")
    public void transactionOutlivesLockWaitAndRenewal() throws SQLException {
        Duration lockWait = sessionSettings.durations(applicationPool(), List.of(LOCK_TIMEOUT)).get(LOCK_TIMEOUT);
        Duration longerThanLockWaitAndRenewal = lockWait.plus(leadershipSettings.heartbeat()).plus(MARGIN);

        await().during(longerThanLockWaitAndRenewal).atMost(longerThanLockWaitAndRenewal.plus(MARGIN))
                .until(leadership::leader);
    }

    @Когда("незавершённая транзакция разбора зафиксирована")
    public void transactionCommitted() throws InterruptedException, ExecutionException, TimeoutException {
        commit.countDown();
        Optional.ofNullable(heldTransaction).orElseThrow().get(FINISH_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    @Тогда("в журнале Дата-референса записей о потере роли ведущего — {int}")
    public void roleLostEntries(int expected) {
        assertThat(journal.events().stream()
                .filter(event -> event.getLevel() == Level.INFO && event.getFormattedMessage().contains(ROLE_LOST)))
                .as("записи журнала о потере роли ведущего")
                .hasSize(expected);
    }

    @Когда("другой экземпляр Дата-референса пытается перехватить роль ведущего")
    public void otherInstanceTriesToTakeOver() {
        takeOverAttempt = CompletableFuture.runAsync(stand::takeOver);
    }

    @Тогда("попытка другого экземпляра перехватить роль ведущего ещё не завершилась")
    public void takeOverAttemptStillWaits() {
        assertThat(Optional.ofNullable(takeOverAttempt).orElseThrow())
                .as("попытка другого экземпляра перехватить роль ведущего")
                .isNotDone();
    }

    @Тогда("попытка другого экземпляра перехватить роль ведущего завершилась")
    public void takeOverAttemptFinished() throws InterruptedException, ExecutionException, TimeoutException {
        Optional.ofNullable(takeOverAttempt).orElseThrow().get(FINISH_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    @After(order = AFTER_DATABASE_RESTORED_BEFORE_LOCK_RELEASE)
    public void finishHeldTransactionAndAttempt() throws InterruptedException, ExecutionException, TimeoutException {
        commit.countDown();
        finish(heldTransaction);
        finish(takeOverAttempt);
    }

    private HikariDataSource applicationPool() throws SQLException {
        return dataSource.unwrap(RestReadDataSource.class).applicationPool();
    }

    private static void finish(@Nullable CompletableFuture<Void> pending)
            throws InterruptedException, ExecutionException, TimeoutException {

        if (pending != null) {
            pending.get(FINISH_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(FINISH_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
