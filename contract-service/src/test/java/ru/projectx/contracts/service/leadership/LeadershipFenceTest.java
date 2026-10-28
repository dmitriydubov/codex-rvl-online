package ru.projectx.contracts.service.leadership;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import ru.projectx.contracts.repository.database.LeaderLockRepository;
import ru.projectx.contracts.support.TestLeadership;

class LeadershipFenceTest {

    private static final LeaderLock LOCK = TestLeadership.LOCK;

    private final LeaderLockRepository locks = mock();
    private final LeadershipFence fence = new LeadershipFence(locks, LOCK);

    @Test
    void ownLockRow_letsTheTransactionGoOn() {
        given(locks.holdIfOwned(LOCK.group(), LOCK.key(), LOCK.instanceId())).willReturn(true);

        assertThatCode(fence::holdUntilCommit).doesNotThrowAnyException();
    }

    @Test
    void lockRowOfAnotherInstanceOrNone_failsTheTransactionNamingTheInstance() {
        given(locks.holdIfOwned(LOCK.group(), LOCK.key(), LOCK.instanceId())).willReturn(false);

        assertThatThrownBy(fence::holdUntilCommit)
                .isInstanceOf(LeadershipLostException.class)
                .hasMessageContaining(LOCK.instanceId());
    }
}
