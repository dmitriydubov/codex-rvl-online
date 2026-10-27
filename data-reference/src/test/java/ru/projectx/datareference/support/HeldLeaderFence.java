package ru.projectx.datareference.support;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.datareference.service.leadership.LeadershipFence;

public final class HeldLeaderFence implements AutoCloseable {

    private static final Duration OPEN_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration COMMIT_TIMEOUT = Duration.ofSeconds(30);

    private final CountDownLatch held = new CountDownLatch(1);
    private final CountDownLatch commit = new CountDownLatch(1);
    private final CompletableFuture<Void> transaction;

    private HeldLeaderFence(PlatformTransactionManager transactions, LeadershipFence fence) {
        this.transaction = CompletableFuture.runAsync(
                () -> new TransactionTemplate(transactions).executeWithoutResult(status -> hold(fence)));
    }

    public static HeldLeaderFence open(PlatformTransactionManager transactions, LeadershipFence fence) {
        HeldLeaderFence heldFence = new HeldLeaderFence(transactions, fence);
        await().atMost(OPEN_TIMEOUT).until(() -> heldFence.held.getCount() == 0 || heldFence.transaction.isDone());
        if (heldFence.transaction.isCompletedExceptionally()) {
            heldFence.transaction.join();
        }
        return heldFence;
    }

    @Override
    public void close() throws InterruptedException, ExecutionException, TimeoutException {
        commit.countDown();
        transaction.get(COMMIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    private void hold(LeadershipFence fence) {
        fence.holdUntilCommit();
        held.countDown();
        awaitQuietly(commit);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(COMMIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
