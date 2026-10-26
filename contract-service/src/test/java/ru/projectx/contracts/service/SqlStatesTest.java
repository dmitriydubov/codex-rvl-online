package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SqlStatesTest {

    @Test
    void deepest_takesStateOfTheInnermostSqlException() {
        RuntimeException failure = new RuntimeException(new SQLException("outer", "08006",
                new SQLException("inner", "55P03")));

        assertThat(SqlStates.deepest(failure)).contains("55P03");
        assertThat(SqlStates.deepestOrNone(failure)).isEqualTo("55P03");
    }

    @Test
    void deepest_keepsOuterStateWhenInnerHasNone() {
        RuntimeException failure = new RuntimeException(new SQLException("outer", "08006", new SQLException("inner")));

        assertThat(SqlStates.deepest(failure)).contains("08006");
    }

    @Test
    void deepest_isEmptyWithoutSqlException() {
        assertThat(SqlStates.deepest(new IllegalStateException("нет базы"))).isEmpty();
        assertThat(SqlStates.deepestOrNone(new IllegalStateException("нет базы"))).isEqualTo("нет");
    }

    @ParameterizedTest
    @ValueSource(strings = {"08006", "08001", "40001", "40P01", "53300", "55P03", "57014"})
    void transientState_holdsForConnectionTransactionResourceLockAndCancelFailures(String state) {
        assertThat(SqlStates.transientState(state)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"23505", "22001", "42P01", "55000", "57P01", "XX000", "", "0"})
    void transientState_doesNotHoldForFailuresThatRepeatOnRetry(String state) {
        assertThat(SqlStates.transientState(state)).isFalse();
    }
}
