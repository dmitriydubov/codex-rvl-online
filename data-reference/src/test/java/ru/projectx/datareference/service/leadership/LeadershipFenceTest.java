package ru.projectx.datareference.service.leadership;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import ru.projectx.datareference.repository.database.LeaderLockRepository;

class LeadershipFenceTest {

    private static final LeaderLock LOCK =
            new LeaderLock("data-reference", "onec-background", "0b5f3c1e-7a2d-4e8b-9c6f-1d2e3f4a5b6c");

    private final LeaderLockRepository locks = mock(LeaderLockRepository.class);

    @Test
    void ownLockRow_letsTheTransactionGoOn() {
        when(locks.holdIfOwned(LOCK.group(), LOCK.key(), LOCK.instanceId())).thenReturn(true);

        assertThatCode(() -> new LeadershipFence(locks, LOCK).holdUntilCommit()).doesNotThrowAnyException();
    }

    @Test
    void lockRowOfAnotherInstance_stopsTheTransactionNamingThisInstance() {
        when(locks.holdIfOwned(LOCK.group(), LOCK.key(), LOCK.instanceId())).thenReturn(false);

        assertThatThrownBy(() -> new LeadershipFence(locks, LOCK).holdUntilCommit())
                .isInstanceOf(LeadershipLostException.class)
                .hasMessageContaining(LOCK.instanceId());
    }
}
