package ru.projectx.contracts.service.integration;

import ru.projectx.contracts.service.SqlStates;

public record StorageOutage(String sqlState, String failureType) {

    static StorageOutage of(RuntimeException failure) {
        return new StorageOutage(SqlStates.deepestOrNone(failure), failure.getClass().getName());
    }
}
