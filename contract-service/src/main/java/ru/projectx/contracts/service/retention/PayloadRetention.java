package ru.projectx.contracts.service.retention;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;

public class PayloadRetention {

    private final Duration payload;
    private final int batchSize;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final BackgroundLeadership leadership;
    private final PayloadEraser eraser;

    public interface PayloadEraser {

        int eraseBefore(Instant cutoff, int batch);
    }

    protected PayloadRetention(Duration payload, int batchSize, TransactionTemplate transaction, Clock clock,
            BackgroundLeadership leadership, PayloadEraser eraser) {
        this.payload = payload;
        this.batchSize = batchSize;
        this.transaction = transaction;
        this.clock = clock;
        this.leadership = leadership;
        this.eraser = eraser;
    }

    public int eraseExpired() {
        return leadership.countAsLeader(this::eraseExpiredAsLeader);
    }

    private int eraseExpiredAsLeader() {
        Instant cutoff = clock.instant().minus(payload);
        int erased = 0;
        int lastBatch = batchSize;
        while (lastBatch == batchSize) {
            lastBatch = transaction.execute(status -> eraser.eraseBefore(cutoff, batchSize));
            erased += lastBatch;
        }
        return erased;
    }
}
