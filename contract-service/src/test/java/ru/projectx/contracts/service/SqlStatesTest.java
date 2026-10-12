package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

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
}
