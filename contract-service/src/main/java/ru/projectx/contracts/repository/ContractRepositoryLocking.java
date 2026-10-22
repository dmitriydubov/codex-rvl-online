package ru.projectx.contracts.repository;

import ru.projectx.contracts.entity.Contract;

public interface ContractRepositoryLocking {

    void lockFresh(Contract contract);
}
