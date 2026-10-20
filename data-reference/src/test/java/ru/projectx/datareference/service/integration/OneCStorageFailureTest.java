package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;
import ru.projectx.datareference.service.leadership.LeadershipLostException;

class OneCStorageFailureTest {

    private static final String TEXT = "отказ";

    @ParameterizedTest
    @ValueSource(strings = {"22P02", "22P05", "22021", "22000"})
    void dataExceptionClass_isPoisonCarryingItsCode(String sqlState) {
        OneCStorageFailure failure = OneCStorageFailure.of(withSqlState(sqlState));

        assertThat(failure).isEqualTo(new OneCStorageFailure.Poison(sqlState));
    }

    @Test
    void lostLeadership_isNeitherPoisonNorOutage() {
        assertThat(OneCStorageFailure.of(new LeadershipLostException("0b5f3c1e-7a2d-4e8b-9c6f-1d2e3f4a5b6c")))
                .isEqualTo(new OneCStorageFailure.LeadershipLost());
    }

    @Test
    void uniqueViolation_isDuplicateRace() {
        assertThat(OneCStorageFailure.of(withSqlState("23505"))).isEqualTo(new OneCStorageFailure.DuplicateRace());
    }

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

    @Test
    void deepestSqlStateWins() {
        var nested = new DataAccessResourceFailureException(TEXT,
                new SQLException(TEXT, "23505", new SQLException(TEXT, "22P05")));

        assertThat(OneCStorageFailure.of(nested)).isEqualTo(new OneCStorageFailure.Poison("22P05"));
    }

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
