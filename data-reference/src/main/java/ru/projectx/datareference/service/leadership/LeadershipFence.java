package ru.projectx.datareference.service.leadership;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.repository.database.LeaderLockRepository;

@Component
public class LeadershipFence {

    private final LeaderLockRepository locks;
    private final LeaderLock lock;

    public LeadershipFence(LeaderLockRepository locks, LeaderLock lock) {
        this.locks = locks;
        this.lock = lock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void holdUntilCommit() {
        if (!locks.holdIfOwned(lock.group(), lock.key(), lock.instanceId())) {
            throw new LeadershipLostException(lock.instanceId());
        }
    }
}
