package ru.projectx.contracts.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import ru.projectx.contracts.entity.Contract;

class ContractRepositoryLockingImpl implements ContractRepositoryLocking {

    private final EntityManager entityManager;

    ContractRepositoryLockingImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void lockFresh(Contract contract) {
        entityManager.refresh(contract, LockModeType.PESSIMISTIC_WRITE);
    }
}
