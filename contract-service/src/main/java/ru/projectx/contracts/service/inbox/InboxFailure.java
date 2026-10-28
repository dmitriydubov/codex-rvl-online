package ru.projectx.contracts.service.inbox;

import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.service.database.SqlStates;
import ru.projectx.contracts.service.database.TemporaryFailures;

public sealed interface InboxFailure {

    String reason();

    String sqlState();

    static InboxFailure of(RuntimeException failure) {
        if (failure instanceof ContractViolationException violation) {
            return new Permanent(violation.getMessage(), SqlStates.NONE);
        }
        if (TemporaryFailures.temporary(failure)) {
            return Transient.of(failure);
        }
        return Permanent.of(failure);
    }

    record Permanent(String reason, String sqlState) implements InboxFailure {

        static Permanent of(RuntimeException failure) {
            return SqlStates.deepest(failure)
                    .map(state -> new Permanent("отказ базы данных, SQLSTATE " + state, state))
                    .orElseGet(() -> new Permanent("внутренняя ошибка обработки: " + failure.getClass().getName(),
                            SqlStates.NONE));
        }
    }

    record Transient(String reason, String sqlState) implements InboxFailure {

        static Transient database(String sqlState) {
            return new Transient("временный отказ базы данных, SQLSTATE " + sqlState, sqlState);
        }

        static Transient of(RuntimeException failure) {
            return TemporaryFailures.serviceReason(failure).map(reason -> new Transient(reason, SqlStates.NONE))
                    .or(() -> TemporaryFailures.transientSqlState(failure).map(Transient::database))
                    .orElseGet(() -> dataAccess(failure));
        }

        private static Transient dataAccess(RuntimeException failure) {
            Throwable cause = TemporaryFailures.temporaryCause(failure).orElseThrow();
            return new Transient("временный отказ доступа к данным: " + cause.getClass().getName(),
                    SqlStates.deepestOrNone(failure));
        }
    }
}
