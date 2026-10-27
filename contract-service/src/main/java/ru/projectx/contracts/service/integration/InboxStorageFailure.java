package ru.projectx.contracts.service.integration;

import java.util.Optional;
import ru.projectx.contracts.service.SqlStates;
import ru.projectx.contracts.service.leadership.LeadershipLostException;

sealed interface InboxStorageFailure {

    String DATA_EXCEPTION_CLASS = "22";
    String UNIQUE_VIOLATION = "23505";

    static InboxStorageFailure of(RuntimeException failure) {
        if (failure instanceof LeadershipLostException) {
            return new LeadershipLost();
        }
        return ofDatabase(failure);
    }

    private static InboxStorageFailure ofDatabase(RuntimeException failure) {
        Optional<String> sqlState = SqlStates.deepest(failure);
        if (sqlState.filter(code -> code.startsWith(DATA_EXCEPTION_CLASS)).isPresent()) {
            return new Poison(sqlState.get());
        }
        if (sqlState.filter(UNIQUE_VIOLATION::equals).isPresent()) {
            return new DuplicateRace();
        }
        return new Outage(StorageOutage.of(failure));
    }

    record Poison(String sqlState) implements InboxStorageFailure {
    }

    record DuplicateRace() implements InboxStorageFailure {
    }

    record LeadershipLost() implements InboxStorageFailure {
    }

    record Outage(StorageOutage outage) implements InboxStorageFailure {
    }
}
