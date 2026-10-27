package ru.projectx.datareference.acceptance;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class DatabaseSwitch {

    private volatile @Nullable String failingSqlState;

    void failConnectionsWith(String sqlState) {
        failingSqlState = sqlState;
    }

    void restore() {
        failingSqlState = null;
    }

    Optional<String> failure() {
        return Optional.ofNullable(failingSqlState);
    }
}
