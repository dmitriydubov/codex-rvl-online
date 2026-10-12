package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InboxStorageFailureTest {

    @ParameterizedTest
    @ValueSource(strings = {"22001", "22P02", "22P05", "23502", "23514"})
    void bodyTheDatabaseNeverAccepts_isRejectedForever(String sqlState) {
        InboxStorageFailure failure = InboxStorageFailure.of(new IllegalStateException(new SQLException("база", sqlState)));

        assertThat(failure.rejectedForever()).isTrue();
        assertThat(failure.sqlState()).isEqualTo(sqlState);
        assertThat(failure.failureType()).isEqualTo("java.lang.IllegalStateException");
    }

    @ParameterizedTest
    @ValueSource(strings = {"23505", "55P03", "08006", "57014", "40001"})
    void transientFailureOrDuplicateRace_isNotRejectedForever(String sqlState) {
        assertThat(InboxStorageFailure.of(new IllegalStateException(new SQLException("база", sqlState)))
                .rejectedForever()).isFalse();
    }

    @Test
    void failureWithoutSqlState_isNotRejectedForever() {
        InboxStorageFailure failure = InboxStorageFailure.of(new IllegalStateException("нет базы"));

        assertThat(failure.rejectedForever()).isFalse();
        assertThat(failure.sqlState()).isEqualTo("нет");
    }
}
