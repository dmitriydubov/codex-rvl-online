package ru.projectx.contracts.service.integration;

import ru.projectx.contracts.service.SqlStates;

record InboxStorageFailure(String failureType, String sqlState) {

    private static final String DATA_EXCEPTION_CLASS = "22";
    private static final String INTEGRITY_CLASS = "23";
    private static final String UNIQUE_VIOLATION = "23505";

    static InboxStorageFailure of(RuntimeException failure) {
        return new InboxStorageFailure(failure.getClass().getName(), SqlStates.deepestOrNone(failure));
    }

    boolean rejectedForever() {
        if (sqlState.startsWith(DATA_EXCEPTION_CLASS)) {
            return true;
        }
        return sqlState.startsWith(INTEGRITY_CLASS) && !UNIQUE_VIOLATION.equals(sqlState);
    }
}
