package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;

/**
 * Судьба сообщения после отказа записи решается по коду SQLSTATE самого глубокого исключения JDBC. Постоянный
 * отказ — только класс 22 (тело, которое база не принимает); гонка доставок — 23505; всё прочее, включая
 * неизвестный код и отказ без кода, — временный отказ хранилища, после которого потребители ждут базу.
 */
class OneCStorageFailureTest {

    private static final String TEXT = "отказ";

    @ParameterizedTest
    @ValueSource(strings = {"22P02", "22P05", "22021", "22000"})
    void dataExceptionClass_isPoisonCarryingItsCode(String sqlState) {
        OneCStorageFailure failure = OneCStorageFailure.of(withSqlState(sqlState));

        assertThat(failure).isEqualTo(new OneCStorageFailure.Poison(sqlState));
    }

    @Test
    void uniqueViolation_isDuplicateRace() {
        assertThat(OneCStorageFailure.of(withSqlState("23505"))).isEqualTo(new OneCStorageFailure.DuplicateRace());
    }

    /** Другие нарушения ограничений — не гонка: 23502 и 23514 говорят об ошибке схемы или кода, повтор их не лечит. */
    @ParameterizedTest
    @ValueSource(strings = {"08001", "08006", "08003", "55P03", "57014", "57P01", "53300", "23502", "23514", "XX000", "2"})
    void connectionLockTimeoutAndUnknownCodes_areOutageWithCodeAndType(String sqlState) {
        OneCStorageFailure failure = OneCStorageFailure.of(withSqlState(sqlState));

        assertThat(failure).isEqualTo(new OneCStorageFailure.Outage(
                new StorageOutage(sqlState, DataIntegrityViolationException.class.getName())));
    }

    @Test
    void failureWithoutSqlState_isOutageNamedWithoutCode() {
        OneCStorageFailure failure = OneCStorageFailure.of(new TransactionSystemException(TEXT));

        assertThat(failure).isEqualTo(new OneCStorageFailure.Outage(
                new StorageOutage("нет", TransactionSystemException.class.getName())));
    }

    /** Код берётся у самого глубокого исключения JDBC: внешнее 23505 поверх внутреннего 22P05 — отравленное тело. */
    @Test
    void deepestSqlStateWins() {
        var nested = new DataAccessResourceFailureException(TEXT,
                new SQLException(TEXT, "23505", new SQLException(TEXT, "22P05")));

        assertThat(OneCStorageFailure.of(nested)).isEqualTo(new OneCStorageFailure.Poison("22P05"));
    }

    /** Отказ записи карантина читается как отказ хранилища, каким бы ни был его код: тело bytea принимает любое. */
    @Test
    void anyFailure_readsAsOutageWithItsCodeAndType() {
        assertThat(OneCStorageFailure.Outage.of(withSqlState("22P05")))
                .isEqualTo(new OneCStorageFailure.Outage(
                        new StorageOutage("22P05", DataIntegrityViolationException.class.getName())));
    }

    private static RuntimeException withSqlState(String sqlState) {
        return new DataIntegrityViolationException(TEXT, new SQLException(TEXT, sqlState));
    }
}
