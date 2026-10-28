package ru.projectx.contracts.service.leadership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.support.IntegrationTest;

@IntegrationTest
@TestPropertySource(properties = "projectx.database.session-timeouts.lock-timeout=1s")
class LeaderRenewalBehindFenceIT {

    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration HEARTBEAT = Duration.ofSeconds(1);
    private static final Duration FENCE_HELD = LOCK_TIMEOUT.plus(HEARTBEAT.multipliedBy(2)).plusMillis(500);
    private static final Duration WAIT = Duration.ofSeconds(10);

    @Autowired
    private LeadershipFence fence;

    @Autowired
    private BackgroundLeadership leadership;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void fenceHeldLongerThanLockTimeoutAndHeartbeat_keepsTheRole() throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CompletableFuture<Void> fenced = CompletableFuture.runAsync(() -> new TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                    fence.holdUntilCommit();
                    held.countDown();
                    awaitQuietly(commit);
                }));
        assertThat(held.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        await().during(FENCE_HELD).atMost(FENCE_HELD.plus(WAIT)).until(leadership::leader);

        commit.countDown();
        fenced.get(WAIT.toSeconds(), TimeUnit.SECONDS);
        assertThat(leadership.leader()).isTrue();
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
