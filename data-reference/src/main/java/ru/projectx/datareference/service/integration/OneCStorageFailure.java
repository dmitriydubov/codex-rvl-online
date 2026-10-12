package ru.projectx.datareference.service.integration;

import java.util.Optional;
import ru.projectx.datareference.service.SqlStates;

sealed interface OneCStorageFailure {

    String DATA_EXCEPTION_CLASS = "22";
    String UNIQUE_VIOLATION = "23505";

    static OneCStorageFailure of(RuntimeException failure) {
        Optional<String> sqlState = SqlStates.deepest(failure);
        if (sqlState.filter(code -> code.startsWith(DATA_EXCEPTION_CLASS)).isPresent()) {
            return new Poison(sqlState.get());
        }
        if (sqlState.filter(UNIQUE_VIOLATION::equals).isPresent()) {
            return new DuplicateRace();
        }
        return Outage.of(failure);
    }

    record Poison(String sqlState) implements OneCStorageFailure {
    }

    record DuplicateRace() implements OneCStorageFailure {
    }

    record Outage(StorageOutage outage) implements OneCStorageFailure {

        static Outage of(RuntimeException failure) {
            return new Outage(new StorageOutage(SqlStates.deepestOrNone(failure), failure.getClass().getName()));
        }
    }
}
