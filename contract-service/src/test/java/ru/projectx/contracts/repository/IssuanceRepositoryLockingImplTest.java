package ru.projectx.contracts.repository;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class IssuanceRepositoryLockingImplTest {

    private final EntityManager entityManager = mock();
    private final IssuanceRepositoryLockingImpl<LmPolicy> locking = new IssuanceRepositoryLockingImpl<>(entityManager);

    @Test
    void records_areRefreshedWithAWriteLockInAscendingOrderOfTheirIdsWhateverTheOrderGiven() {
        LmPolicy later = Issuances.policy("0199b6c4-0000-7000-8000-000000000002", Tasks.CHILD);
        LmPolicy earlier = Issuances.policy("0199b6c4-0000-7000-8000-000000000001", Tasks.SPOUSE);

        locking.lockFresh(List.of(later, earlier));

        InOrder order = inOrder(entityManager);
        order.verify(entityManager).refresh(earlier, LockModeType.PESSIMISTIC_WRITE);
        order.verify(entityManager).refresh(later, LockModeType.PESSIMISTIC_WRITE);
        order.verifyNoMoreInteractions();
    }

    @Test
    void noRecords_touchNothing() {
        locking.lockFresh(List.of());

        verifyNoInteractions(entityManager);
    }
}
