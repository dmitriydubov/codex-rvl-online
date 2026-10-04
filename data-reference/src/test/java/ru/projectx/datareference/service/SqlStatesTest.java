package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Код SQLSTATE берётся у самого глубокого {@link SQLException} в цепочке причин: драйвер PostgreSQL
 * кладёт код поверх {@code IOException}, а Spring и Hibernate оборачивают ошибку драйвера своими
 * исключениями без кода.
 */
class SqlStatesTest {

    private static final String TEXT = "отказ";

    @Test
    void deepestSqlException_givesItsState_notTheOuterOne() {
        var outer = new SQLException(TEXT, "23505", new SQLException(TEXT, "22P05"));

        assertThat(SqlStates.deepest(new DataAccessResourceFailureException(TEXT, outer))).hasValue("22P05");
    }

    @Test
    void sqlExceptionAboveIoCause_givesItsOwnState() {
        var lost = new SQLException(TEXT, "08006", new IOException("reset"));

        assertThat(SqlStates.deepest(lost)).hasValue("08006");
    }

    @Test
    void noSqlExceptionInChain_isEmpty() {
        assertThat(SqlStates.deepest(new IllegalStateException(TEXT, new IOException("reset")))).isEmpty();
    }

    @Test
    void sqlExceptionWithoutState_isEmpty() {
        assertThat(SqlStates.deepest(new SQLException(TEXT))).isEmpty();
    }

    /** Исключение JDBC без кода глубже в цепочке не затирает код, который стоит выше него. */
    @Test
    void deeperSqlExceptionWithoutState_doesNotHideTheOuterState() {
        var outer = new SQLException(TEXT, "22P05", new SQLException(TEXT));

        assertThat(SqlStates.deepest(outer)).hasValue("22P05");
    }

    @Test
    void deepestOrNone_namesMissingStateWithTheWordNo() {
        assertThat(SqlStates.deepestOrNone(new IllegalStateException(TEXT))).isEqualTo("нет");
        assertThat(SqlStates.deepestOrNone(new SQLException(TEXT, "08006"))).isEqualTo("08006");
    }
}
