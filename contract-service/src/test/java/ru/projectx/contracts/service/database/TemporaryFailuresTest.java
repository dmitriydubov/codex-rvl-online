package ru.projectx.contracts.service.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;
import ru.projectx.contracts.exception.TemporaryFailureException;

class TemporaryFailuresTest {

    @Test
    void lockRefusedWithoutDatabaseDetail_isTemporary() {
        assertThat(TemporaryFailures.temporary(new CannotAcquireLockException("строка занята"))).isTrue();
    }

    @Test
    void statementCancelledByTimeout_isTemporary() {
        assertThat(TemporaryFailures.temporary(new QueryTimeoutException("отменено"))).isTrue();
    }

    @Test
    void poolTimeoutWithoutSqlState_isTemporaryEvenWhenWrappedByTheTransactionManager() {
        RuntimeException failure = new CannotCreateTransactionException("нет соединения",
                new IllegalStateException(new SQLTransientConnectionException("пул исчерпан")));

        assertThat(TemporaryFailures.temporary(failure)).isTrue();
    }

    @Test
    void temporaryFailureOfTheService_isTemporary() {
        assertThat(TemporaryFailures.temporary(new TemporaryFailureException("справочник недоступен",
                new IllegalStateException()))).isTrue();
    }

    @Test
    void temporaryFailureDeeperInTheCauses_isTemporary() {
        RuntimeException failure = new IllegalStateException(new TemporaryFailureException("хранилище недоступно",
                new IllegalStateException()));

        assertThat(TemporaryFailures.temporary(failure)).isTrue();
    }

    @Test
    void databaseFailureWithTransientState_isTemporary() {
        RuntimeException failure = new IllegalStateException(new SQLException("взаимная блокировка", "40P01"));

        assertThat(TemporaryFailures.temporary(failure)).isTrue();
    }

    @Test
    void databaseFailureWithStateThatRepeatsOnRetry_isPermanent() {
        RuntimeException failure = new DataIntegrityViolationException("нарушено ограничение",
                new SQLException("нарушено ограничение", "23505"));

        assertThat(TemporaryFailures.temporary(failure)).isFalse();
    }

    @Test
    void brokenInternalCondition_isPermanent() {
        assertThat(TemporaryFailures.temporary(new IllegalStateException("у договора КМ нет записи акцепта")))
                .isFalse();
    }

    @Test
    void serviceReason_isTheMessageOfTheTemporaryFailureOfTheServiceDeeperInTheCauses() {
        RuntimeException failure = new IllegalStateException(new TemporaryFailureException("справочник недоступен",
                new SQLException("взаимная блокировка", "40P01")));

        assertThat(TemporaryFailures.serviceReason(failure)).contains("справочник недоступен");
    }

    @Test
    void serviceReason_isAbsentWithoutTemporaryFailureOfTheService() {
        assertThat(TemporaryFailures.serviceReason(new QueryTimeoutException("отменено"))).isEmpty();
    }

    @Test
    void transientSqlState_isTheDeepestStateWhenItIsTemporary() {
        RuntimeException failure = new CannotAcquireLockException("строка занята",
                new SQLException("строка занята", "55P03"));

        assertThat(TemporaryFailures.transientSqlState(failure)).contains("55P03");
    }

    @Test
    void transientSqlState_isAbsentForStateThatRepeatsOnRetry() {
        RuntimeException failure = new IllegalStateException(new SQLException("нарушено ограничение", "23505"));

        assertThat(TemporaryFailures.transientSqlState(failure)).isEmpty();
    }

    @Test
    void temporaryCause_isTheCauseOfTemporaryTypeInTheChain() {
        SQLTransientConnectionException poolTimeout = new SQLTransientConnectionException("пул исчерпан");
        RuntimeException failure = new CannotCreateTransactionException("нет соединения",
                new IllegalStateException(poolTimeout));

        assertThat(TemporaryFailures.temporaryCause(failure)).containsSame(poolTimeout);
    }

    @Test
    void temporaryCause_isAbsentForFailureOfOtherTypes() {
        RuntimeException failure = new IllegalStateException(new SQLException("взаимная блокировка", "40P01"));

        assertThat(TemporaryFailures.temporaryCause(failure)).isEmpty();
    }
}
