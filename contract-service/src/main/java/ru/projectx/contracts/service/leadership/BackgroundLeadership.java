package ru.projectx.contracts.service.leadership;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.Context;

public class BackgroundLeadership implements Candidate {

    private static final Logger log = LoggerFactory.getLogger(BackgroundLeadership.class);

    private final LeaderLock lock;
    private volatile boolean leader;
    private volatile @Nullable Context election;
    private final AtomicBoolean steppingDown = new AtomicBoolean();

    public BackgroundLeadership(LeaderLock lock) {
        this.lock = lock;
    }

    @Override
    public String getRole() {
        return lock.role();
    }

    @Override
    public String getId() {
        return lock.instanceId();
    }

    @Override
    public void onGranted(Context context) {
        election = context;
        steppingDown.set(false);
        leader = true;
        log.info("экземпляр {} стал ведущим: приём из очередей и проходы по расписанию идут на нём", lock.instanceId());
    }

    @Override
    public void onRevoked(Context context) {
        leader = false;
        log.info("экземпляр {} больше не ведущий: приём из очередей и проходы по расписанию на нём остановлены",
                lock.instanceId());
    }

    public boolean leader() {
        return leader;
    }

    public void runAsLeader(Runnable pass) {
        runByRole(pass, () -> { });
    }

    public void runByRole(Runnable asLeader, Runnable asStandby) {
        if (!leader) {
            asStandby.run();
            return;
        }
        try {
            asLeader.run();
        }
        catch (LeadershipLostException lost) {
            lostDuringPass();
        }
    }

    public int countAsLeader(IntSupplier pass) {
        if (!leader) {
            return 0;
        }
        try {
            return pass.getAsInt();
        }
        catch (LeadershipLostException lost) {
            lostDuringPass();
            return 0;
        }
    }

    public void stepDown() {
        Optional.ofNullable(election)
                .filter(Context::isLeader)
                .ifPresent(this::yieldOnce);
    }

    private void lostDuringPass() {
        log.info("проход прерван: экземпляр {} больше не держит блокировку ведущего", lock.instanceId());
        stepDown();
    }

    private void yieldOnce(Context leading) {
        if (steppingDown.compareAndSet(false, true)) {
            leading.yield();
        }
    }
}
