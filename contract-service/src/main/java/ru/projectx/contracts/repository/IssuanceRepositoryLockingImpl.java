package ru.projectx.contracts.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Comparator;
import java.util.List;
import ru.projectx.contracts.entity.issuance.IssuanceRecord;

class IssuanceRepositoryLockingImpl<R extends IssuanceRecord> implements IssuanceRepositoryLocking<R> {

    private final EntityManager entityManager;

    IssuanceRepositoryLockingImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void lockFresh(List<R> records) {
        for (R record : records.stream().sorted(Comparator.comparing(IssuanceRecord::id)).toList()) {
            entityManager.refresh(record, LockModeType.PESSIMISTIC_WRITE);
        }
    }
}
