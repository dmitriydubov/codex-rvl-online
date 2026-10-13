package ru.projectx.contracts.service.inbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.exception.TemporaryFailureException;

class InboxFailureTest {

    @Test
    void contractViolation_isPermanentWithItsReason() {
        InboxFailure failure = InboxFailure.of(new ContractViolationException("нет обязательного поля data.date_from"));

        assertThat(failure).isEqualTo(new InboxFailure.Permanent("нет обязательного поля data.date_from", "нет"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"55P03", "57014", "08001", "08006", "40001", "40P01", "53300"})
    void temporaryDatabaseStates_areTransientWithTheirState(String sqlState) {
        InboxFailure failure = InboxFailure.of(new RuntimeException(new SQLException("база", sqlState)));

        assertThat(failure).isEqualTo(InboxFailure.Transient.database(sqlState));
        assertThat(failure.reason()).isEqualTo("временный отказ базы данных, SQLSTATE " + sqlState);
    }

    @ParameterizedTest
    @ValueSource(strings = {"23505", "22P02", "42P01"})
    void otherDatabaseStates_arePermanentNamingTheState(String sqlState) {
        InboxFailure failure = InboxFailure.of(new RuntimeException(new SQLException("база", sqlState)));

        assertThat(failure).isEqualTo(new InboxFailure.Permanent("отказ базы данных, SQLSTATE " + sqlState, sqlState));
    }

    @Test
    void temporaryFailure_isTransientWithItsReasonAndWithoutSqlState() {
        InboxFailure failure = InboxFailure.of(new TemporaryFailureException("справочный сервис недоступен",
                new IllegalStateException("Тестов")));

        assertThat(failure).isEqualTo(new InboxFailure.Transient("справочный сервис недоступен", "нет"));
    }

    @Test
    void unexpectedException_isPermanentNamingOnlyItsClass() {
        InboxFailure failure = InboxFailure.of(new IllegalStateException("Тестов"));

        assertThat(failure).isEqualTo(
                new InboxFailure.Permanent("внутренняя ошибка обработки: java.lang.IllegalStateException", "нет"));
    }
}
