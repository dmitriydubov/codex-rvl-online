package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.hibernate.exception.DataException;
import org.hibernate.exception.GenericJDBCException;
import org.hibernate.exception.LockAcquisitionException;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.hibernate.HibernateExceptionTranslator;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.service.database.DatabaseOutages;
import tools.jackson.databind.exc.JsonNodeException;
import tools.jackson.databind.node.MissingNode;

class ParseFailuresTest {

    private static final String PERSONAL_DATA = "Иванова Мария Петровна, 1980-05-17";

    private final ParseFailures failures =
            new ParseFailures(new DatabaseOutages(new HibernateExceptionTranslator()));

    @Test
    void lockTimeout_isTemporary_withTypeAndSqlStateButNoDriverText() {
        var lock = new LockAcquisitionException(PERSONAL_DATA, new SQLException(PERSONAL_DATA, "55P03"), "update");

        ParseFailure failure = failures.classify(lock);

        assertThat(failure).isEqualTo(new ParseFailure.Temporary(
                "временный отказ базы данных: CannotAcquireLockException, SQLSTATE 55P03",
                "CannotAcquireLockException", "55P03"));
    }

    @Test
    void sessionClosedForIdleTransaction_isTemporary() {
        var closed = new GenericJDBCException(PERSONAL_DATA, new SQLException(PERSONAL_DATA, "25P03"));

        ParseFailure failure = failures.classify(closed);

        assertThat(failure).isEqualTo(new ParseFailure.Temporary(
                "временный отказ базы данных: JpaSystemException, SQLSTATE 25P03", "JpaSystemException", "25P03"));
    }

    @Test
    void valueRejectedByDatabase_isPermanent_withTypeAndSqlStateButNoDriverText() {
        var overflow = new DataException(PERSONAL_DATA, new SQLException(PERSONAL_DATA, "22003"), "insert");

        ParseFailure failure = failures.classify(overflow);

        assertThat(failure).isEqualTo(new ParseFailure.Permanent(
                "база данных отклонила запись: DataIntegrityViolationException, SQLSTATE 22003",
                "DataIntegrityViolationException", "22003"));
    }

    @Test
    void contractViolation_isPermanent_withItsOwnText() {
        ParseFailure failure = failures.classify(new OneCFieldException("поле ИНН длиннее 12 символов"));

        assertThat(failure).isEqualTo(
                new ParseFailure.Permanent("поле ИНН длиннее 12 символов", "OneCFieldException", "нет"));
    }

    @Test
    void unexpectedFailure_isPermanent_namedByTypeOnly() {
        ParseFailure failure = failures.classify(JsonNodeException.from(MissingNode.getInstance(), PERSONAL_DATA));

        assertThat(failure).isEqualTo(new ParseFailure.Permanent(
                "непредвиденная ошибка разбора: JsonNodeException", "JsonNodeException", "нет"));
    }
}
