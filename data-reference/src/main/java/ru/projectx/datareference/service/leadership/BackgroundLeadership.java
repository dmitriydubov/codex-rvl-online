package ru.projectx.datareference.service.leadership;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.leader.Candidate;
import org.springframework.integration.leader.Context;

public class BackgroundLeadership implements Candidate {

    private static final Logger log = LoggerFactory.getLogger(BackgroundLeadership.class);

    private final LeaderLock lock;
    private volatile boolean leader;

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
        leader = true;
        log.info("экземпляр {} стал ведущим: приём из 1С:УСК и задачи по расписанию идут на нём", lock.instanceId());
    }

    @Override
    public void onRevoked(Context context) {
        leader = false;
        log.info("экземпляр {} больше не ведущий: приём из 1С:УСК и задачи по расписанию на нём остановлены",
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
            log.info("проход по расписанию прерван: экземпляр {} больше не держит блокировку ведущего", lock.instanceId());
        }
    }
}
