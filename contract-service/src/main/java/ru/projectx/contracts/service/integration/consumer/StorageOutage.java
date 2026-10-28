package ru.projectx.contracts.service.integration.consumer;

import ru.projectx.contracts.service.database.SqlStates;

public record StorageOutage(String sqlState, String failureType) {

    public static StorageOutage of(RuntimeException failure) {
        return new StorageOutage(SqlStates.deepestOrNone(failure), failure.getClass().getName());
    }
}
