package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/**
 * Судьба сообщения после отказа записи решается по коду SQLSTATE. Постоянный отказ — только класс 22
 * (тело, которое база не принимает); гонка доставок — 23505; всё прочее, включая неизвестный код и отказ
 * без кода, — временный отказ хранилища, после которого потребители ждут базу.
 */
class OneCStorageFailureTest {

    @ParameterizedTest
    @ValueSource(strings = {"22P02", "22P05", "22021", "22000"})
    void dataExceptionClass_isPoison(String sqlState) {
        assertThat(OneCStorageFailure.ofSqlState(Optional.of(sqlState))).isEqualTo(OneCStorageFailure.POISON);
    }

    @Test
    void uniqueViolation_isDuplicateRace() {
        assertThat(OneCStorageFailure.ofSqlState(Optional.of("23505"))).isEqualTo(OneCStorageFailure.DUPLICATE_RACE);
    }

    /** Другие нарушения ограничений — не гонка: 23502 и 23514 говорят об ошибке схемы или кода, повтор их не лечит. */
    @ParameterizedTest
    @ValueSource(strings = {"08001", "08006", "08003", "55P03", "57014", "57P01", "53300", "23502", "23514", "XX000", "2"})
    void connectionLockTimeoutAndUnknownCodes_areOutage(String sqlState) {
        assertThat(OneCStorageFailure.ofSqlState(Optional.of(sqlState))).isEqualTo(OneCStorageFailure.OUTAGE);
    }

    @Test
    void failureWithoutSqlState_isOutage() {
        assertThat(OneCStorageFailure.ofSqlState(Optional.empty())).isEqualTo(OneCStorageFailure.OUTAGE);
    }
}
