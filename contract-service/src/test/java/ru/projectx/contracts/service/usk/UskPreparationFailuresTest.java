package ru.projectx.contracts.service.usk;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;
import ru.projectx.contracts.exception.TemporaryFailureException;

class UskPreparationFailuresTest {

    @Test
    void lockRefusedWithoutDatabaseDetail_isTemporary() {
        assertThat(UskPreparationFailures.temporary(new CannotAcquireLockException("строка занята"))).isTrue();
    }

    @Test
    void statementCancelledByTimeout_isTemporary() {
        assertThat(UskPreparationFailures.temporary(new QueryTimeoutException("отменено"))).isTrue();
    }

    @Test
    void poolTimeoutWithoutSqlState_isTemporaryEvenWhenWrappedByTheTransactionManager() {
        RuntimeException failure = new CannotCreateTransactionException("нет соединения",
                new IllegalStateException(new SQLTransientConnectionException("пул исчерпан")));

        assertThat(UskPreparationFailures.temporary(failure)).isTrue();
    }

    @Test
    void temporaryFailureOfTheService_isTemporary() {
        assertThat(UskPreparationFailures.temporary(new TemporaryFailureException("справочник недоступен",
                new IllegalStateException()))).isTrue();
    }

    @Test
    void temporaryFailureDeeperInTheCauses_isTemporary() {
        RuntimeException failure = new IllegalStateException(new TemporaryFailureException("хранилище недоступно",
                new IllegalStateException()));

        assertThat(UskPreparationFailures.temporary(failure)).isTrue();
    }

    @Test
    void databaseFailureWithTransientState_isTemporary() {
        RuntimeException failure = new IllegalStateException(new SQLException("взаимная блокировка", "40P01"));

        assertThat(UskPreparationFailures.temporary(failure)).isTrue();
    }

    @Test
    void databaseFailureWithStateThatRepeatsOnRetry_isPermanent() {
        RuntimeException failure = new DataIntegrityViolationException("нарушено ограничение",
                new SQLException("нарушено ограничение", "23505"));

        assertThat(UskPreparationFailures.temporary(failure)).isFalse();
    }

    @Test
    void brokenInternalCondition_isPermanent() {
        assertThat(UskPreparationFailures.temporary(new IllegalStateException("у договора КМ нет записи акцепта")))
                .isFalse();
    }
}
